package com.softknife.integraton.tc.client;

import com.softknife.integraton.tc.client.model.post.job.PostBuild;
import com.softknife.integraton.tc.client.model.task.BuildExecResult;
import com.softknife.integraton.tc.client.model.task.BuildExecutorTask;
import com.softknife.util.common.TaskState;
import com.softknife.util.common.TaskStatus;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * A TeamCity task is SUCCESS only when a build really finished successfully.
 *
 * <p>Each of these produced SUCCESS before the fix: a build that could not be triggered, a build
 * still running when the wait ran out, and a task with nothing to run.
 */
public class TCBuildExecutorOutcomeTest {

    private MockWebServer server;

    @BeforeMethod
    public void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void stop() throws IOException {
        server.shutdown();
    }

    private BuildExecutorTask task(boolean sequential) {
        PostBuild postBuild = TCHelper.buildTeamCityTriggerBuildRequest("project", "config", null, null, null);
        BuildExecutorTask task = new BuildExecutorTask();
        task.setPostBuild(List.of(postBuild));
        task.setMaxAttemptBuildCounter(1);
        task.setMaxWaitTime(10);
        task.setDeploymentSequential(sequential);
        return task;
    }

    private BuildExecutorTask run(BuildExecutorTask task) throws Exception {
        TeamCityClient client = new TeamCityClient(server.url("/").toString(), "token");
        return new TCBuildExecutor(task, client).executeBuildsAsync().get(60, TimeUnit.SECONDS);
    }

    @Test(description = "A trigger rejected with HTTP 500 fails the task")
    public void rejectedTriggerFails() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"boom\"}"));

        BuildExecutorTask result = run(task(false));

        assertEquals(result.getTaskStatus(), TaskStatus.FAILURE.getValue());
        BuildExecResult build = result.getBuildMetaData().get("config");
        assertEquals(build.getState(), TaskState.ABORTED.getValue());
        assertTrue(build.getErrors().stream().anyMatch(e -> e.contains("HTTP 500")), String.valueOf(build.getErrors()));
    }

    @Test(description = "A build still running when the wait runs out fails the task, even though TeamCity says SUCCESS so far")
    public void buildStillRunningFails() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().startsWith("/app/rest/buildQueue")) {
                    return new MockResponse().setResponseCode(200).setBody("{\"id\": 7, \"state\": \"queued\"}");
                }
                return new MockResponse().setResponseCode(200).setBody("{\"id\": 7, \"state\": \"running\", \"status\": \"SUCCESS\"}");
            }
        });

        BuildExecutorTask result = run(task(true));

        // Prove the build was really triggered and polled, so the failure is about the wait, not the trigger.
        assertTrue(server.getRequestCount() >= 3, "requests: " + server.getRequestCount());
        assertEquals(result.getTaskStatus(), TaskStatus.FAILURE.getValue());
        BuildExecResult build = result.getBuildMetaData().get("config");
        assertEquals(build.getState(), TaskState.ABORTED.getValue());
        assertTrue(build.getErrors().stream().anyMatch(e -> e.contains(TcConstant.ERROR_RUNNING_EXCEEDED_TIME)), String.valueOf(build.getErrors()));
    }

    @Test(description = "A build that finished with SUCCESS passes the task")
    public void finishedSuccessPasses() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().startsWith("/app/rest/buildQueue")) {
                    return new MockResponse().setResponseCode(200).setBody("{\"id\": 8, \"state\": \"queued\"}");
                }
                return new MockResponse().setResponseCode(200).setBody("{\"id\": 8, \"state\": \"finished\", \"status\": \"SUCCESS\"}");
            }
        });

        BuildExecutorTask result = run(task(false));

        assertEquals(result.getTaskStatus(), TaskStatus.SUCCESS.getValue());
    }

    @Test(description = "A task with no build results is FAILURE, never SUCCESS from nothing")
    public void emptyTaskFails() throws Exception {
        BuildExecutorTask task = new BuildExecutorTask();
        TeamCityClient client = new TeamCityClient(server.url("/").toString(), "token");

        BuildExecutorTask result = new TCBuildExecutor(task, client).finalizeResult(task);

        assertEquals(result.getTaskStatus(), TaskStatus.FAILURE.getValue());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.containsValue(TcConstant.ERROR_NO_BUILD_RESULTS)));
    }
}
