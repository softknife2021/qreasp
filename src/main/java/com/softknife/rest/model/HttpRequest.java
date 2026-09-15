package com.softknife.rest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sasha Matsaylo on 2020-11-29
 * @project qreasp
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequest {

    private String httpMethod;
    private String url;
    private String uri;
    private Map<String, String> urlParams;
    private Map<String, String> queryParams;
    private Map<String, String> headers;
    private String requestBody;
    private String contentType;

    public HttpRequest(String httpMethod, String url) {
        this.httpMethod = httpMethod;
        this.url = url;
    }

    /**
     * An independent copy: the parameter and header maps are new maps, so changing the copy never
     * changes the original. Clients that keep request templates copy them per call — mutating the
     * shared template carried one call's parameters into the next, and let two threads corrupt each other.
     *
     * @param source the request to copy; may be null
     * @return a copy with non-null, mutable maps, or null when {@code source} is null
     */
    public static HttpRequest copyOf(HttpRequest source) {
        if (source == null) {
            return null;
        }
        return new HttpRequest(source.httpMethod, source.url, source.uri,
                mutableCopy(source.urlParams), mutableCopy(source.queryParams), mutableCopy(source.headers),
                source.requestBody, source.contentType);
    }

    private static Map<String, String> mutableCopy(Map<String, String> map) {
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }
}
