package com.softknife.integration.stash.client;

import com.softknife.integraton.stash.client.StashRestClient;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The URL each Stash call actually produces, recorded by a local server.
 */
public class StashRequestIsolationTest {

    private MockWebServer server;
    private StashRestClient client;

    @BeforeMethod
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new StashRestClient(server.url("/").toString(), "token", "proj", "repo");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private RecordedRequest send(Response response) throws InterruptedException {
        if (response != null) {
            response.close();
        }
        return server.takeRequest(5, TimeUnit.SECONDS);
    }

    @Test(description = "A v2 call substitutes the workspace instead of sending {workSpace} literally")
    public void v2CallUsesTheWorkspace() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        RecordedRequest recorded = send(client.getCommitsInRangeV2());

        assertEquals(recorded.getPath(), "/2.0/repositories/proj/repo/commits");
    }

    @Test(description = "One call's paging parameters do not leak into the next call")
    public void pagingParametersDoNotLeakBetweenCalls() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        RecordedRequest first = send(client.getCommitsInRangeV1("a", "b", 5, 10));
        RecordedRequest second = send(client.getCommitsInRangeV1("a", "b", null, null));

        // Prove the first call really carried the values, so their absence below is meaningful.
        assertTrue(first.getPath().contains("start=5"), first.getPath());
        assertTrue(first.getPath().contains("limit=10"), first.getPath());
        assertFalse(second.getPath().contains("start=5"), "start leaked into the next call: " + second.getPath());
        assertTrue(second.getPath().contains("limit=100"), "template default lost: " + second.getPath());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "User Password must be provided",
            description = "The basic-auth constructor refuses a blank password")
    public void blankPasswordIsRefused() throws Exception {
        new StashRestClient(server.url("/").toString(), "user", " ", "proj", "repo");
    }
}
