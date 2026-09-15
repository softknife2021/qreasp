package com.softknife.rest.client;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * Logs each request and response line with its headers.
 *
 * <p>Registered as a network interceptor, so it runs after the application interceptors that add
 * {@code Authorization}. Credentials therefore reach this class, and every header or query
 * parameter that can carry one is replaced with {@value #REDACTED} before anything is logged.
 *
 * @author Sasha Matsaylo on 9/17/19
 * @project qreasp
 */
public class LoggingInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /** What a sensitive value is replaced with in the log. */
    public static final String REDACTED = "***";

    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    /** A header or query parameter whose name contains one of these is treated as a secret. */
    private static final String[] SENSITIVE_FRAGMENTS = {
            "token", "secret", "password", "passwd", "apikey", "api-key", "api_key", "session", "credential"};

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();

        long t1 = System.nanoTime();
        logger.info("Sending request {} on {}\n{}", redact(request.url()), chain.connection(), redact(request.headers()));
        try {
            Response response = chain.proceed(request);

            long t2 = System.nanoTime();
            logger.info("Received response for {} with {} in {}ms\n{}", redact(response.request().url()), response.code(),
                    String.format(Locale.ROOT, "%.1f", (t2 - t1) / 1e6d), redact(response.headers()));

            return response;
        } catch (IOException e) {
            long duration = System.nanoTime() - t1;
            logger.error("Request FAILED for {} after {} ms. Exception: {}",
                    redact(request.url()), TimeUnit.NANOSECONDS.toMillis(duration), e.getMessage());
            throw e;
        }
    }

    /**
     * True when a header or query parameter with this name can carry a credential.
     *
     * @param name header or parameter name, any case
     * @return whether its value must never be logged
     */
    public static boolean isSensitive(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (SENSITIVE_NAMES.contains(lower)) {
            return true;
        }
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Headers as one {@code Name: value} line each, with sensitive values replaced.
     *
     * @param headers the headers to render
     * @return a loggable representation that carries no credential
     */
    public static String redact(Headers headers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            sb.append(name).append(": ").append(isSensitive(name) ? REDACTED : headers.value(i)).append('\n');
        }
        return sb.toString();
    }

    /**
     * The URL with the values of sensitive query parameters replaced.
     *
     * @param url the request URL
     * @return a loggable URL that carries no credential in its query string
     */
    public static String redact(HttpUrl url) {
        if (url.querySize() == 0) {
            return url.toString();
        }
        HttpUrl.Builder builder = url.newBuilder();
        for (String name : url.queryParameterNames()) {
            if (isSensitive(name)) {
                builder.setQueryParameter(name, REDACTED);
            }
        }
        return builder.build().toString();
    }
}
