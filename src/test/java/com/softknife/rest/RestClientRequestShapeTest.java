package com.softknife.rest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.softknife.http.helper.ExecutionState;
import com.softknife.http.helper.HttpRequestHelper;
import com.softknife.http.helper.model.HttpExecutionResult;
import com.softknife.rest.client.HttpMethods;
import com.softknife.rest.client.LoggingInterceptor;
import com.softknife.rest.client.RestClientHelper;
import com.softknife.rest.model.HttpRequest;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * What actually goes over the wire, recorded by a local MockWebServer.
 *
 * <p>Every test enters through the public API a consumer uses — {@link HttpRequestHelper} or
 * {@link RestClientHelper#executeRequest} — and asserts on the request the server received, not on
 * an object built along the way.
 */
public class RestClientRequestShapeTest {

    private static final String SECRET_TOKEN = "s3cr3t-bearer-value-7f2k9";

    private MockWebServer server;

    @BeforeMethod
    public void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void stopServer() throws IOException {
        server.shutdown();
    }

    private HttpRequest request(HttpMethods method, String path) {
        return new HttpRequest(method.getValue(), server.url(path).toString());
    }

    // ---- 2.1 credentials never reach the log ------------------------------

    @Test(description = "A Bearer token is logged as *** and never in clear text")
    public void bearerTokenIsRedactedInTheRequestLog() throws Exception {
        Logger interceptorLogger = (Logger) LoggerFactory.getLogger(LoggingInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        interceptorLogger.addAppender(appender);
        try {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            OkHttpClient client = RestClientHelper.getInstance().buildBearerClient(SECRET_TOKEN);

            HttpExecutionResult result = HttpRequestHelper.executeHttpRequest(client, request(HttpMethods.GET, "/secure?access_token=" + SECRET_TOKEN));

            // Prove the credential really was sent, so the absence below means redaction, not a missing header.
            RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
            Assert.assertEquals(recorded.getHeader("Authorization"), "Bearer " + SECRET_TOKEN);
            Assert.assertEquals(result.getStatus(), ExecutionState.COMPLETED);

            StringBuilder logged = new StringBuilder();
            appender.list.forEach(e -> logged.append(e.getFormattedMessage()).append('\n'));
            Assert.assertFalse(appender.list.isEmpty(), "the interceptor should have logged the request");
            Assert.assertFalse(logged.toString().contains(SECRET_TOKEN), "token leaked into the log:\n" + logged);
            Assert.assertTrue(logged.toString().contains("Authorization: " + LoggingInterceptor.REDACTED), logged.toString());
        } finally {
            interceptorLogger.detachAppender(appender);
        }
    }

    @Test(description = "Redaction covers the obvious credential headers and leaves ordinary ones alone")
    public void headerRedactionIsSelective() {
        Headers headers = new Headers.Builder()
                .add("Authorization", "Basic dXNlcjpwYXNz")
                .add("Cookie", "SESSION=abc")
                .add("X-Api-Key", "k-123")
                .add("X-Auth-Token", "t-456")
                .add("Accept", "application/json")
                .build();

        String rendered = LoggingInterceptor.redact(headers);

        Assert.assertFalse(rendered.contains("dXNlcjpwYXNz"));
        Assert.assertFalse(rendered.contains("SESSION=abc"));
        Assert.assertFalse(rendered.contains("k-123"));
        Assert.assertFalse(rendered.contains("t-456"));
        Assert.assertTrue(rendered.contains("Accept: application/json"), "a non-sensitive header must stay readable");
    }

    @Test(description = "A credential in the query string is redacted; other parameters are kept")
    public void queryParameterRedaction() {
        HttpUrl url = HttpUrl.get("http://host/path?page=2&api_key=abc123");

        String rendered = LoggingInterceptor.redact(url);

        Assert.assertFalse(rendered.contains("abc123"), rendered);
        Assert.assertTrue(rendered.contains("page=2"), rendered);
    }

    // ---- 2.2 a non-JSON body is sent, not thrown away ---------------------

    @Test(description = "A form-encoded body is sent and the result is COMPLETED")
    public void nonJsonBodyIsSent() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201));
        HttpRequest request = request(HttpMethods.POST, "/form");
        request.setRequestBody("a=1&b=2");
        request.setContentType("application/x-www-form-urlencoded");

        HttpExecutionResult result = HttpRequestHelper.executeHttpRequest(RestClientHelper.getInstance().buildNoAuthClient(), request);

        Assert.assertEquals(result.getStatus(), ExecutionState.COMPLETED, "error: " + result.getError());
        Assert.assertEquals(result.getStatusCode(), 201);
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertEquals(recorded.getBody().readUtf8(), "a=1&b=2");
        Assert.assertTrue(recorded.getHeader("Content-Type").startsWith("application/x-www-form-urlencoded"));
    }

    // ---- 2.3 every declared method goes out as itself ---------------------

    @Test(description = "HEAD is sent as HEAD")
    public void headIsSentAsHead() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        RestClientHelper.getInstance().executeRequest(RestClientHelper.getInstance().buildNoAuthClient(), request(HttpMethods.HEAD, "/h")).close();

        Assert.assertEquals(server.takeRequest(5, TimeUnit.SECONDS).getMethod(), "HEAD");
    }

    @Test(description = "OPTIONS is sent as OPTIONS")
    public void optionsIsSentAsOptions() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        RestClientHelper.getInstance().executeRequest(RestClientHelper.getInstance().buildNoAuthClient(), request(HttpMethods.OPTIONS, "/o")).close();

        Assert.assertEquals(server.takeRequest(5, TimeUnit.SECONDS).getMethod(), "OPTIONS");
    }

    @Test(description = "Every HttpMethods constant reaches the server under its own name")
    public void everyDeclaredMethodIsSentAsItself() throws Exception {
        OkHttpClient client = RestClientHelper.getInstance().buildNoAuthClient();
        for (HttpMethods method : HttpMethods.values()) {
            server.enqueue(new MockResponse().setResponseCode(200));
            HttpRequest request = request(method, "/m");
            if (method == HttpMethods.POST || method == HttpMethods.PUT || method == HttpMethods.PATCH) {
                request.setRequestBody("{\"k\":1}");
            }
            RestClientHelper.getInstance().executeRequest(client, request).close();
            Assert.assertEquals(server.takeRequest(5, TimeUnit.SECONDS).getMethod(), method.getValue(),
                    "sent the wrong method for " + method);
        }
    }

    @Test(description = "A Content-Type given in headers is sent once, not duplicated")
    public void contentTypeIsNotDuplicated() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        HttpRequest request = request(HttpMethods.POST, "/ct");
        request.setHeaders(headers);
        request.setRequestBody("{\"k\":1}");

        RestClientHelper.getInstance().executeRequest(RestClientHelper.getInstance().buildNoAuthClient(), request).close();

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertEquals(recorded.getHeaders().values("Content-Type").size(), 1, recorded.getHeaders().toString());
    }

    // ---- client default headers do not wipe the request's own -------------

    @Test(description = "A client's default headers are added without wiping the request's own headers")
    public void clientDefaultsDoNotWipeRequestHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        OkHttpClient client = RestClientHelper.getInstance()
                .buildBearerClient("t", Map.of("X-Default", "d", "Accept", "application/json"));
        HttpRequest request = request(HttpMethods.GET, "/headers");
        request.setHeaders(new HashMap<>(Map.of("X-Request", "r", "Accept", "text/plain")));

        RestClientHelper.getInstance().executeRequest(client, request).close();

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        Assert.assertEquals(recorded.getHeader("X-Default"), "d", "client default missing");
        Assert.assertEquals(recorded.getHeader("X-Request"), "r", "the request's own header was wiped");
        Assert.assertEquals(recorded.getHeader("Accept"), "text/plain", "the request's header must win over the default");
        Assert.assertEquals(recorded.getHeader("Authorization"), "Bearer t");
    }

    // ---- 2.10 no response is FAILED ---------------------------------------

    @Test(description = "A refused connection is FAILED with the reason, not SKIPPED")
    public void refusedConnectionIsFailed() throws Exception {
        String url = server.url("/gone").toString();
        server.shutdown();

        HttpExecutionResult result = HttpRequestHelper.executeHttpRequest(
                RestClientHelper.getInstance().buildNoAuthClient(), new HttpRequest(HttpMethods.GET.getValue(), url));

        Assert.assertEquals(result.getStatus(), ExecutionState.FAILED);
        Assert.assertFalse(result.isSuccessful());
        Assert.assertNotNull(result.getError());
    }

    // ---- SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE ------------------

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*not an http or https URL.*",
            description = "A malformed URL is refused by name, not with a NullPointerException")
    public void malformedUrlIsRefused() {
        RestClientHelper.getInstance().addQueryParams("not a url", Map.of("a", "b"));
    }
}
