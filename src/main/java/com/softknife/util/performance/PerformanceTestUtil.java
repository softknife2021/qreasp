package com.softknife.util.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softknife.exception.RecordNotFound;
import com.softknife.http.helper.ExecutionState;
import com.softknife.http.helper.HttpRequestHelper;
import com.softknife.http.helper.HttpResultAnalyzer;
import com.softknife.http.helper.model.HttpExecutionResult;
import com.softknife.http.helper.model.PerfExecResult;
import com.softknife.rest.model.HttpRequest;
import freemarker.template.TemplateException;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class PerformanceTestUtil {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /** How long one iteration may take before it is cut short and reported as truncated. */
    public static final Duration DEFAULT_ITERATION_TIMEOUT = Duration.ofMinutes(1);

    /**
     * Runs the request with a thread count that ramps from {@code threadPoolSizeStart} to
     * {@code threadPoolSizeEnd} over {@code iterations}, each request after a random delay in
     * [{@code startTimeRange}, {@code endTimeRange}) milliseconds.
     *
     * @return the overall result as pretty JSON; {@code truncated} is true when any iteration hit its timeout
     */
    public static String runPerformanceTest(
            OkHttpClient client,
            HttpRequest request,
            ObjectMapper mapper,
            int threadPoolSizeStart,
            int threadPoolSizeEnd,
            int iterations,
            Long startTimeRange,
            Long endTimeRange
    ) throws InterruptedException, JsonProcessingException, TemplateException, RecordNotFound, IOException {
        return runPerformanceTest(client, request, mapper, threadPoolSizeStart, threadPoolSizeEnd, iterations,
                startTimeRange, endTimeRange, DEFAULT_ITERATION_TIMEOUT);
    }

    /**
     * As {@link #runPerformanceTest(OkHttpClient, HttpRequest, ObjectMapper, int, int, int, Long, Long)},
     * with an explicit per-iteration timeout.
     */
    public static String runPerformanceTest(
            OkHttpClient client,
            HttpRequest request,
            ObjectMapper mapper,
            int threadPoolSizeStart,
            int threadPoolSizeEnd,
            int iterations,
            Long startTimeRange,
            Long endTimeRange,
            Duration iterationTimeout
    ) throws InterruptedException, JsonProcessingException {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be at least 1, was " + iterations);
        }
        long minDelay = startTimeRange == null ? 0L : startTimeRange;
        long maxDelay = endTimeRange == null ? minDelay : endTimeRange;
        if (minDelay < 0 || maxDelay < minDelay) {
            throw new IllegalArgumentException("Delay range must satisfy 0 <= startTimeRange <= endTimeRange, was ["
                    + startTimeRange + ", " + endTimeRange + "]");
        }

        List<HttpExecutionResult> allExecutionResults = new ArrayList<>();
        boolean anyIterationTruncated = false;

        for (int iteration = 0; iteration < iterations; iteration++) {
            int threadPoolSize = (iteration == iterations - 1)
                    ? threadPoolSizeEnd
                    : getThreadCountForIteration(threadPoolSizeStart, threadPoolSizeEnd, iterations, iteration);
            if (threadPoolSize <= 0) {
                logger.info("Iteration {} has {} threads; nothing to send", iteration + 1, threadPoolSize);
                continue;
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
            CountDownLatch latch = new CountDownLatch(threadPoolSize);
            List<HttpExecutionResult> iterationResults = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadPoolSize; i++) {
                executorService.submit(() -> {
                    try {
                        long delay = maxDelay > minDelay ? ThreadLocalRandom.current().nextLong(minDelay, maxDelay) : minDelay;
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                        logger.debug("Executing request in thread: {}", Thread.currentThread().getName());
                        iterationResults.add(HttpRequestHelper.executeHttpRequest(client, request));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        iterationResults.add(failed("Interrupted before the request was sent"));
                    } catch (Exception e) {
                        // Recorded, never dropped: a request that vanished from the count would read as a smaller test.
                        logger.error("Error executing request in thread {}: {}", Thread.currentThread().getName(), e.getMessage());
                        iterationResults.add(failed(e.getClass().getSimpleName() + ": " + e.getMessage()));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean finished = latch.await(iterationTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (finished) {
                executorService.shutdown();
            } else {
                anyIterationTruncated = true;
                logger.error("PERF_ITERATION_TRUNCATED - iteration {} did not finish within {}; {} of {} requests completed. "
                        + "The results below are partial.", iteration + 1, iterationTimeout, iterationResults.size(), threadPoolSize);
                executorService.shutdownNow();
            }
            executorService.awaitTermination(10, TimeUnit.SECONDS);

            List<HttpExecutionResult> snapshot;
            synchronized (iterationResults) {
                snapshot = new ArrayList<>(iterationResults);
            }
            allExecutionResults.addAll(snapshot);
            PerfExecResult iterationPerfResult = HttpResultAnalyzer.analyzePerformanceResults(snapshot);
            iterationPerfResult.setTruncated(!finished);
            logger.info("Iteration {} results: {}", iteration + 1, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(iterationPerfResult));
        }
        PerfExecResult overallPerfResult = HttpResultAnalyzer.analyzePerformanceResults(allExecutionResults);
        overallPerfResult.setTruncated(anyIterationTruncated);
        String jsonPerfResult = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(overallPerfResult);
        logger.info("Overall performance results ({} iterations): {}", iterations, jsonPerfResult);
        return jsonPerfResult;
    }

    private static HttpExecutionResult failed(String error) {
        HttpExecutionResult result = new HttpExecutionResult();
        result.setStatus(ExecutionState.FAILED);
        result.setSuccessful(false);
        result.setError(error);
        return result;
    }

    /**
     * Calculates the thread count for a given iteration, moving evenly from start to end over the total number of iterations.
     *
     * <p>The step is fractional: 1 to 3 over 4 iterations is 1, 1, 2, 3 — integer division made it 1, 1, 1, 3.
     *
     * @param start           The starting thread count (inclusive).
     * @param end             The ending thread count (inclusive).
     * @param totalIterations The total number of iterations to distribute the thread count across.
     * @param currentIteration The current iteration index (zero-based).
     * @return The thread count to use for the current iteration.
     */
    public static int getThreadCountForIteration(int start, int end, int totalIterations, int currentIteration) {
        if (totalIterations <= 1) return start;
        if (currentIteration >= totalIterations - 1) return end;
        double step = (double) (end - start) / (totalIterations - 1);
        return start + (int) Math.floor(step * currentIteration + 1e-9);
    }
}
