package com.softknife.util.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.rest.client.HttpMethods;
import com.softknife.rest.client.RestClientHelper;
import com.softknife.rest.model.HttpRequest;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * PerformanceTestUtil against a local server: empty delay ranges, invalid ranges, truncation.
 */
public class PerformanceTestUtilFixesTest {

    private final ObjectMapper mapper = GlobalResourceManager.getInstance().getObjectMapper();
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

    private void respond(long headersDelayMs) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200).setBody("{}")
                        .setHeadersDelay(headersDelayMs, TimeUnit.MILLISECONDS);
            }
        });
    }

    private HttpRequest get() {
        return new HttpRequest(HttpMethods.GET.getValue(), server.url("/perf").toString());
    }

    @Test(description = "A zero delay range runs every request instead of throwing inside each thread")
    public void zeroDelayRangeRuns() throws Exception {
        respond(0);

        String json = PerformanceTestUtil.runPerformanceTest(RestClientHelper.getInstance().buildNoAuthClientNoLogging(),
                get(), mapper, 1, 2, 2, 0L, 0L);

        assertEquals((int) JsonPath.read(json, "$.totalRequests"), 3, json);
        assertEquals((int) JsonPath.read(json, "$.successRequests"), 3, json);
        assertEquals((boolean) JsonPath.read(json, "$.truncated"), false, json);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*startTimeRange <= endTimeRange.*",
            description = "An inverted delay range is refused before any thread starts")
    public void invertedRangeIsRefused() throws Exception {
        PerformanceTestUtil.runPerformanceTest(RestClientHelper.getInstance().buildNoAuthClientNoLogging(),
                get(), mapper, 1, 1, 1, 5L, 1L);
    }

    @Test(description = "An iteration that outlives its timeout is reported as truncated, not as a complete result")
    public void slowIterationIsTruncated() throws Exception {
        respond(3000);

        String json = PerformanceTestUtil.runPerformanceTest(RestClientHelper.getInstance().buildNoAuthClientNoLogging(),
                get(), mapper, 2, 2, 1, 0L, 0L, Duration.ofMillis(300));

        assertTrue(JsonPath.read(json, "$.truncated"), json);
    }

    @Test(description = "The thread ramp uses a fractional step")
    public void rampUsesFractionalStep() {
        int[] expected = {1, 1, 2, 3};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(PerformanceTestUtil.getThreadCountForIteration(1, 3, 4, i), expected[i], "iteration " + i);
        }
    }
}
