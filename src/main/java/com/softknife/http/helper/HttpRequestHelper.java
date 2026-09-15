package com.softknife.http.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softknife.data.templating.TemplateManager;
import com.softknife.exception.RecordNotFound;
import com.softknife.http.helper.model.HttpExecutionResult;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.rest.client.RestClientHelper;
import com.softknife.rest.model.HttpRequest;
import freemarker.template.TemplateException;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

/**
 * Executes an {@link HttpRequest} and captures the outcome as an {@link HttpExecutionResult}.
 *
 * @author alexander matsaylo on 4/14/22
 * @project backend-platform-test-automation
 */
public final class HttpRequestHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper mapper = GlobalResourceManager.getInstance().getObjectMapper();

    /** Longest request body written to the debug log; the rest is summarised as a character count. */
    static final int MAX_LOGGED_BODY_CHARS = 2000;

    private HttpRequestHelper() {
    }

    /**
     * Sends the request and records what came back.
     *
     * <p>A request that never got a response — connection refused, timeout, unknown host — is
     * {@link ExecutionState#FAILED} with the exception message in {@code error}. A response of any
     * status code is {@link ExecutionState#COMPLETED}; whether the status was acceptable is the
     * caller's decision, and {@code isSuccessful} reflects 2xx.
     */
    public static HttpExecutionResult executeHttpRequest(OkHttpClient okClient, HttpRequest httpRestRequest) {
        HttpExecutionResult execResult = new HttpExecutionResult();
        logRequestBody(httpRestRequest.getRequestBody());
        try (Response response = RestClientHelper.getInstance().executeRequest(okClient, httpRestRequest)) {
            execResult.setStatusCode(response.code());
            execResult.setSuccessful(response.isSuccessful());
            execResult.setExecutionTime(getDurationMillis(response));
            execResult.setConvertedExecutionTime(longToReadableTime(response));
            // OkHttp 5 never returns a null body from a network response.
            execResult.setResponseBody(response.body().string());
            execResult.setStatus(ExecutionState.COMPLETED);
        } catch (IOException e) {
            logger.error("Request {} {} failed: {}", httpRestRequest.getHttpMethod(), httpRestRequest.getUrl(), e.getMessage());
            execResult.setError(e.getLocalizedMessage());
            execResult.setSuccessful(false);
            execResult.setStatus(ExecutionState.FAILED);
        }
        return execResult;
    }

    private static String longToReadableTime(Response response) {
        long duration = getDurationMillis(response);
        long seconds = duration / 1000;
        long millis = duration % 1000;
        return seconds + " second" + (seconds != 1 ? "s" : "") + " " + millis + " milliseconds";
    }

    /**
     * Returns the duration of the request in milliseconds as a long value for calculations
     * @param response The HTTP response
     * @return The duration in milliseconds as a long
     */
    private static long getDurationMillis(Response response) {
        return response.receivedResponseAtMillis() - response.sentRequestAtMillis();
    }

    /**
     * Logs the request body at DEBUG. JSON is pretty-printed; anything else — form data, XML, plain
     * text — is logged as it is. Logging must never stop a request from being sent.
     */
    static String logRequestBody(String body) {
        if (body == null || body.isEmpty() || !logger.isDebugEnabled()) {
            return null;
        }
        String rendered;
        try {
            rendered = mapper.readTree(StringEscapeUtils.unescapeJson(body)).toPrettyString();
        } catch (Exception notJson) {
            rendered = body;
        }
        if (rendered.length() > MAX_LOGGED_BODY_CHARS) {
            rendered = rendered.substring(0, MAX_LOGGED_BODY_CHARS)
                    + "... (" + (rendered.length() - MAX_LOGGED_BODY_CHARS) + " more chars)";
        }
        logger.debug("Request body : \n{}", rendered);
        return rendered;
    }


    public static HttpRequest buildHttpRequestFromTemplate(TemplateManager templateManager, String templateName, String templateVersion, String jsonPayload) throws TemplateException, RecordNotFound, IOException {

        String payload = templateManager.processTemplateWithJsonInput(templateName, templateVersion, jsonPayload);
        return mapper.readValue(payload, HttpRequest.class);
    }

}
