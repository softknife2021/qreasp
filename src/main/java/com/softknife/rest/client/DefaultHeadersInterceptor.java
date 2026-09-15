package com.softknife.rest.client;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds a client's default headers to each request that does not already set them.
 *
 * <p>Replaces the interceptors that called {@code Request.Builder.headers(...)}, which removes every
 * header on the request before adding the defaults — so a request's own headers, including the
 * Content-Type chosen for its body, never reached the server when the client had defaults.
 * A header the request sets itself wins over the default of the same name.
 */
final class DefaultHeadersInterceptor implements Interceptor {

    private final Map<String, String> defaults;

    DefaultHeadersInterceptor(Map<String, String> defaults) {
        this.defaults = defaults == null ? Collections.emptyMap() : new LinkedHashMap<>(defaults);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        if (defaults.isEmpty()) {
            return chain.proceed(original);
        }
        Request.Builder builder = original.newBuilder();
        for (Map.Entry<String, String> header : defaults.entrySet()) {
            if (original.header(header.getKey()) == null && header.getValue() != null) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return chain.proceed(builder.build());
    }
}
