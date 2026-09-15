package com.softknife.integraton.tc.client;

import com.jayway.jsonpath.JsonPath;
import com.softknife.integraton.tc.client.model.post.job.PostBuild;
import com.softknife.integraton.tc.client.model.task.BuildExecResult;
import com.softknife.integraton.tc.client.model.task.BuildExecutorTask;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.util.common.GenericUtils;
import com.softknife.util.common.TaskState;
import com.softknife.util.common.TaskStatus;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.jayway.jsonpath.JsonPath.read;

/**
 * Triggers TeamCity builds and waits for their outcome.
 *
 * <p>A task is SUCCESS only when every build it ran finished with a SUCCESS status. A build that
 * could not be triggered, stayed queued, or was still running when the wait ran out makes the task
 * FAILURE — none of those is evidence that the build passed.
 *
 * @author Sasha Matsaylo
 * @project qreasp
 */
public class TCBuildExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final TeamCityClient teamCityClient;
    /** Written from several threads in parallel mode. */
    private final Map<String, BuildExecResult> buildMetaData;
    private volatile BuildExecutorTask buildExecutorTask;
    /** Guards the task's error list, which parallel builds append to. Private, so no caller can hold it. */
    private final Object errorsLock = new Object();

    public TCBuildExecutor(BuildExecutorTask buildExecutorTask, TeamCityClient teamCityClient) {
        this.buildExecutorTask = buildExecutorTask;
        this.teamCityClient = teamCityClient;
        this.buildMetaData = Collections.synchronizedMap(new LinkedHashMap<>());
    }

    public void initBuildExecutorTask(BuildExecutorTask buildExecutorTask) {
        this.buildExecutorTask = buildExecutorTask;
    }

    private BuildExecResult executeBuild(PostBuild postBuild) {
        String buildTypeId = postBuild.getBuildType().getBuildTypeId();
        logger.info("Starting to process build {}", buildTypeId);
        BuildExecResult buildExecResult = new BuildExecResult();
        buildExecResult.setState(TaskState.STARTED.getValue());
        this.threadSleep(GenericUtils.getRandomNumber(1000, 3000));
        try {
            String buildId;
            String requestBody = GlobalResourceManager.getInstance().getObjectMapper().writeValueAsString(postBuild);
            try (Response triggerJobResp = this.teamCityClient.postBuild(requestBody)) {
                if (triggerJobResp == null) {
                    setExecutionResults(buildExecResult, TaskState.ABORTED.getValue(), TcConstant.ERROR_FAILED_TO_TRIGGER_BUILD, postBuild);
                    return buildExecResult;
                }
                String triggerRespBody = triggerJobResp.body().string();
                if (!triggerJobResp.isSuccessful()) {
                    buildExecResult.setExecutionMetaData(triggerRespBody);
                    setExecutionResults(buildExecResult, TaskState.ABORTED.getValue(),
                            TcConstant.ERROR_FAILED_TO_TRIGGER_BUILD + " (HTTP " + triggerJobResp.code() + ")", postBuild);
                    return buildExecResult;
                }
                buildId = readBuildQueueId(triggerRespBody);
            }
            buildExecResult.setBuildId(buildId);
            setExecutionResults(buildExecResult, TaskState.RUNNING.getValue(), null, postBuild);

            ifBuildInQueueWait(buildId, this.buildExecutorTask.getMaxAttemptBuildCounter(), this.buildExecutorTask.getMaxWaitTime());
            if (whatIsBuildState(buildId).equalsIgnoreCase(TcConstant.BUILD_STATE_QUEUED)) {
                setExecutionResults(buildExecResult, TaskState.ABORTED.getValue(), TcConstant.ERROR_QUEUE_EXCEEDED_TIME, postBuild);
                return buildExecResult;
            }
            setExecutionResults(buildExecResult, TaskState.RUNNING.getValue(), null, postBuild);

            String lastState = this.ifBuildRunningWait(buildExecResult, this.buildExecutorTask.getMaxAttemptBuildCounter(),
                    this.buildExecutorTask.getMaxWaitTime());
            if (TcConstant.BUILD_STATE_FINISHED.equalsIgnoreCase(lastState)) {
                setExecutionResults(buildExecResult, TaskState.FINISHED.getValue(), null, postBuild);
            } else {
                // Still running, or a state we could not read. Marking this FINISHED reported a build
                // that was still running — and so still "SUCCESS so far" in TeamCity — as passed.
                setExecutionResults(buildExecResult, TaskState.ABORTED.getValue(),
                        TcConstant.ERROR_RUNNING_EXCEEDED_TIME + " (last state: " + (StringUtils.isBlank(lastState) ? "unknown" : lastState) + ")",
                        postBuild);
            }
        } catch (Exception e) {
            logger.error("TC_BUILD_NOT_EXECUTED - build {} could not be executed: {}", buildTypeId, e.getMessage(), e);
            setExecutionResults(buildExecResult, TaskState.ABORTED.getValue(), "Build could not be executed: " + e.getMessage(), postBuild);
        }
        return buildExecResult;
    }

    private void setExecutionResults(BuildExecResult buildExecResult, String taskState, @Nullable String taskError, PostBuild postBuild) {
        buildExecResult.setState(taskState);
        if (taskError != null) {
            buildExecResult.getErrors().add(taskError);
        }
        this.buildMetaData.put(postBuild.getBuildType().getBuildTypeId(), buildExecResult);
        this.buildExecutorTask.setBuildMetaData(this.buildMetaData);
    }

    private void ifBuildInQueueWait(String buildQueueId, int maxAttempt, int waitTime) {
        int buildQueueCounter = 0;
        boolean isBuildInQueue = true;
        while (isBuildInQueue && buildQueueCounter <= maxAttempt) {
            String buildState = this.whatIsBuildState(buildQueueId);
            if (buildState.equalsIgnoreCase(TcConstant.BUILD_STATE_QUEUED)) {
                this.threadSleep(waitTime);
            } else {
                isBuildInQueue = false;
            }
            buildQueueCounter++;
        }
    }

    private void threadSleep(int waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting on TeamCity");
        }
    }

    private String readBuildQueueId(String metaData) {
        return Integer.toString(read(metaData, TcConstant.JSON_PATH_BUILD_QUEUE_ID));
    }

    /** The build's state, or "" when TeamCity did not answer. */
    private String whatIsBuildState(String buildQueueId) {
        String responseBody = this.getBuildMetaData(buildQueueId);
        if (responseBody == null) {
            return "";
        }
        String state = read(responseBody, TcConstant.JSON_PATH_BUILD_STATE);
        return state == null ? "" : state;
    }

    private String getBuildMetaData(String buildId) {
        try (Response response = this.teamCityClient.getBuildById(buildId)) {
            if (response != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            logger.error("Could not read metadata for TeamCity build {}: {}", buildId, e.getMessage());
        }
        return null;
    }

    private String setBuildMetaData(BuildExecResult buildExecResult) {
        String buildMetaData = getBuildMetaData(buildExecResult.getBuildId());
        if (StringUtils.isNotBlank(buildMetaData)) {
            buildExecResult.setExecutionMetaData(buildMetaData);
        }
        return buildMetaData;
    }

    /** Waits while the build is running; returns the last state observed. */
    private String ifBuildRunningWait(BuildExecResult buildExecResult, int maxAttempt, int waitTime) {
        String buildState = null;
        int buildRunningCounter = 0;
        boolean isBuildRunning = true;
        while (isBuildRunning && buildRunningCounter <= maxAttempt) {
            buildState = this.whatIsBuildState(buildExecResult.getBuildId());
            if (buildState.equalsIgnoreCase(TcConstant.BUILD_STATE_RUNNING)) {
                setBuildMetaData(buildExecResult);
                this.threadSleep(waitTime);
            } else {
                isBuildRunning = false;
                setBuildMetaData(buildExecResult);
            }
            buildRunningCounter++;
        }
        return buildState;
    }

    private BuildExecutorTask executeBuildsParallel() {
        List<PostBuild> builds = buildExecutorTask.getPostBuild() == null ? List.of() : buildExecutorTask.getPostBuild();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, builds.size()));
        try {
            List<Future<BuildExecResult>> futures = new ArrayList<>();
            for (PostBuild postBuild : builds) {
                futures.add(pool.submit(() -> executeBuild(postBuild)));
            }
            // Wait for every build. The pool used to be shut down inside the loop, after the first
            // build, so a later submit could be rejected.
            for (Future<BuildExecResult> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    logger.error("A parallel TeamCity build failed unexpectedly: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for parallel TeamCity builds");
        } finally {
            pool.shutdownNow();
        }
        return finalizeResult(this.buildExecutorTask);
    }

    private BuildExecutorTask executeBuildsSequential() {
        List<PostBuild> builds = buildExecutorTask.getPostBuild() == null ? List.of() : buildExecutorTask.getPostBuild();
        for (PostBuild postBuild : builds) {
            BuildExecResult buildExecResult = this.executeBuild(postBuild);
            if (buildExecResult.getState().equalsIgnoreCase(TaskState.FINISHED.getValue())) {
                String status = statusOf(buildExecResult);
                if (!TcConstant.BUILD_STATUS_SUCCESS.equalsIgnoreCase(status)) {
                    setBuildErrors(postBuild.getBuildType().getBuildTypeId(), TcConstant.ERROR_PRIORITY_BUILD_FAILURE);
                    if (!this.buildExecutorTask.isContinueIfSequentialDeploymentFail()) {
                        break;
                    }
                }
            }
            else if(buildExecResult.getState().equalsIgnoreCase(TaskState.ABORTED.getValue())){
                if (!this.buildExecutorTask.isContinueIfSequentialDeploymentFail()) {
                    break;
                }
            }
        }
        return finalizeResult(this.buildExecutorTask);
    }

    /** The TeamCity status in the build's metadata, or null when it cannot be read. */
    private String statusOf(BuildExecResult buildExecResult) {
        if (StringUtils.isBlank(buildExecResult.getExecutionMetaData())) {
            return null;
        }
        try {
            return JsonPath.read(buildExecResult.getExecutionMetaData(), TcConstant.JSON_PATH_BUILD_STATUS);
        } catch (Exception e) {
            logger.warn("Could not read the status of build {}: {}", buildExecResult.getBuildId(), e.getMessage());
            return null;
        }
    }

    BuildExecutorTask finalizeResult(BuildExecutorTask buildExecutorTask) {
        String taskStatus = TaskStatus.SUCCESS.getValue();
        Map<String, BuildExecResult> results = buildExecutorTask.getBuildMetaData();
        if (results == null || results.isEmpty()) {
            // Nothing ran, so nothing passed.
            taskStatus = TaskStatus.FAILURE.getValue();
            setBuildErrors("task", TcConstant.ERROR_NO_BUILD_RESULTS);
        } else {
            synchronized (results) {
                for (Map.Entry<String, BuildExecResult> entry : results.entrySet()) {
                    BuildExecResult result = entry.getValue();
                    // A build without a finished state or without metadata is a failure. Both used to be
                    // skipped, so a build that could not even be triggered left the task SUCCESS.
                    boolean finished = TcConstant.BUILD_STATE_FINISHED.equalsIgnoreCase(result.getState());
                    if (!finished || !TcConstant.BUILD_STATUS_SUCCESS.equalsIgnoreCase(statusOf(result))) {
                        taskStatus = TaskStatus.FAILURE.getValue();
                    }
                }
            }
        }
        buildExecutorTask.setTaskStatus(taskStatus);
        buildExecutorTask.setTaskState(TaskState.FINISHED.getValue());
        return buildExecutorTask;
    }

    /**
     * Runs the task on a background thread. The future completes exceptionally if the run itself
     * throws, instead of never completing.
     */
    public Future<BuildExecutorTask> executeBuildsAsync() throws InterruptedException {
        CompletableFuture<BuildExecutorTask> completableFuture = new CompletableFuture<>();
        ExecutorService runner = Executors.newSingleThreadExecutor();
        runner.execute(() -> {
            try {
                completableFuture.complete(this.buildExecutorTask.isDeploymentSequential()
                        ? this.executeBuildsSequential()
                        : this.executeBuildsParallel());
            } catch (Throwable t) {
                logger.error("TC_TASK_FAILED - TeamCity task could not run: {}", t.getMessage(), t);
                completableFuture.completeExceptionally(t);
            }
        });
        // Lets the submitted task finish, then releases the thread; the pool used to leak per call.
        runner.shutdown();
        return completableFuture;
    }

    public BuildExecutorTask getBuildExecutorTask() {
        return buildExecutorTask;
    }

    private void setBuildErrors(String errorKey, String errorValue) {
        Map<String, String> result = new HashMap<>();
        result.put(errorKey, errorValue);
        synchronized (errorsLock) {
            this.buildExecutorTask.getErrors().add(result);
        }
    }
}
