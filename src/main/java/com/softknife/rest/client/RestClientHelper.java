package com.softknife.rest.client;

import com.jayway.jsonpath.JsonPath;
import com.softknife.rest.model.HttpRequest;
import com.softknife.util.common.GenericUtils;
import okhttp3.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Sasha Matsaylo on 9/17/18
 * @project qreasp
 */
public class RestClientHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private volatile OkHttpClient sharedOkHttpClient;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");


    private RestClientHelper() {
        createSharedOkHttpClient();
    }

    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final RestClientHelper INSTANCE = new RestClientHelper();
    }

    public static RestClientHelper getInstance() {
        return Holder.INSTANCE;
    }

    private void createSharedOkHttpClient() {
        this.sharedOkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build();
    }

    private OkHttpClient buildClientFromShared(Interceptor auth, Map<String, String> headers) {
        return sharedOkHttpClient.newBuilder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .addInterceptor(new DefaultHeadersInterceptor(headers))
                .addInterceptor(auth)
                .build();
    }

    private OkHttpClient buildClientFromSharedWithUserInterceptor(Interceptor auth, Interceptor userDefinedInterceptor) {
        return sharedOkHttpClient.newBuilder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .addInterceptor(auth)
                .addInterceptor(userDefinedInterceptor)
                .build();
    }

    public OkHttpClient buildNoAuthClient() {
        return sharedOkHttpClient.newBuilder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .build();
    }

    public OkHttpClient buildNoAuthClientNoLogging() {
        return sharedOkHttpClient.newBuilder()
                .build();
    }

    public OkHttpClient buildClientWithHeaders(Map<String, String> headers, Long connectTimeout, Long readTimeout, Long writeTimeout) {
        return sharedOkHttpClient.newBuilder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .addInterceptor(new DefaultHeadersInterceptor(headers))
                .build();
    }

    public OkHttpClient buildClientWithHeaders(Map<String, String> headers) {
        return sharedOkHttpClient.newBuilder()
                .addInterceptor(new DefaultHeadersInterceptor(headers))
                .build();
    }


    private OkHttpClient buildClientFromSharedWithBearerInterceptor(Interceptor auth, @Nullable Map<String, String> headers) {
        OkHttpClient.Builder builder = sharedOkHttpClient.newBuilder();
        builder.addNetworkInterceptor(new LoggingInterceptor());
        if (headers != null) {
            builder.addInterceptor(new DefaultHeadersInterceptor(headers));
        }
        builder.addInterceptor(auth);
        return builder.build();
    }

    private OkHttpClient buildClientFromSharedUserDefinedInterceptor(Interceptor userInterceptor, @Nullable Map<String, String> headers) {
        OkHttpClient.Builder builder = sharedOkHttpClient.newBuilder();
        builder.addNetworkInterceptor(new LoggingInterceptor());
        if (headers != null) {
            builder.addInterceptor(new DefaultHeadersInterceptor(headers));
        }
        builder.addInterceptor(userInterceptor);
        return builder.build();
    }


    //we can pass headers
    public OkHttpClient buildBasicAuthClient(String userName, String password, Map<String, String> headers) {
        return buildClientFromShared(new BasicAuthInterceptor(userName, password), headers);
    }

    public OkHttpClient buildBasicAuthClientWithCustomInterceptor(String userName, String password, Interceptor userDefinedInterceptor) {
        return buildClientFromSharedWithUserInterceptor(new BasicAuthInterceptor(userName, password), userDefinedInterceptor);
    }

    public OkHttpClient buildBearerClientWithCustomInterceptor(String token, Interceptor userDefinedInterceptor) {
        return buildClientFromSharedWithUserInterceptor(new BearerAuthInterceptor(token), userDefinedInterceptor);
    }

    //we can pass headers
    public OkHttpClient buildBasicAuthClient(String userName, String password) {
        Map<String, String> headers = new HashMap<>();
        return buildClientFromShared(new BasicAuthInterceptor(userName, password), headers);
    }

    public OkHttpClient buildBearerClient(String token) throws Exception {
        Map<String, String> headers = new HashMap<>();
        return buildClientFromSharedWithBearerInterceptor(new BearerAuthInterceptor(token), headers);
    }

    public OkHttpClient buildBearerClient(String token, Map<String, String> headers) throws Exception {
        return buildClientFromSharedWithBearerInterceptor(new BearerAuthInterceptor(token), headers);
    }


    public OkHttpClient buildTrustedHttpClient(Map<String, String> headers) {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            OkHttpClient.Builder builder = sharedOkHttpClient.newBuilder();
            builder.followRedirects(true);
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            OkHttpClient okHttpClient = builder.build();
            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public OkHttpClient registerLoggerInterceptor(OkHttpClient okHttpClient) {
        return okHttpClient.newBuilder()
                .addInterceptor(new LoggingInterceptor())
                .build();
    }

    public OkHttpClient registerBasicAuthInterceptor(OkHttpClient okHttpClient, String userName, String password) {
        return okHttpClient.newBuilder()
                .addInterceptor(new BasicAuthInterceptor(userName, password))
                .build();
    }

    public void registerLoggerInterceptorForSharedClient() {
        this.sharedOkHttpClient = this.sharedOkHttpClient.newBuilder()
                .addInterceptor(new LoggingInterceptor())
                .build();
    }

    public OkHttpClient buildOkHttpClient(Map<String, String> headers) {
        return sharedOkHttpClient.newBuilder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .addInterceptor(new DefaultHeadersInterceptor(headers))
                .build();
    }


    /**
     * Sends the request. The request's own headers are applied to this call only.
     * The caller owns the returned {@link Response} and must close it.
     */
    public Response executeRequest(OkHttpClient okHttpClient, HttpRequest httpRequest) throws IOException {
        return okHttpClient.newCall(convertToOkHttpRequest(httpRequest)).execute();
    }


    private String substituteUrlParams(String url, @Nullable Map<String, String> urlParams) {
        if (MapUtils.isNotEmpty(urlParams)) {
            return GenericUtils.substituteVariables(url, urlParams);
        }
        return url;
    }


    public String addQueryParams(String url, Map<String, String> queryParam) {
        HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
        if (parsed == null) {
            throw new IllegalArgumentException(ConstantsErrors.INVALID_URL + ": '" + url + "' is not an http or https URL");
        }
        HttpUrl.Builder httpBuilder = parsed.newBuilder();
        if (queryParam != null) {
            for (Map.Entry<String, String> param : queryParam.entrySet()) {
                httpBuilder.addQueryParameter(param.getKey(), param.getValue());
            }

        }
        return httpBuilder.build().toString();
    }

    private RequestBody createRequestBody(String requestBody, @Nullable String contentType) {
        MediaType mediaType = null;
        if (contentType == null) {
            mediaType = JSON;
        } else {
            mediaType = MediaType.get(contentType);
        }
        if (requestBody == null) {
            return RequestBody.create("", mediaType);
        } else {
            return RequestBody.create(requestBody, mediaType);
        }
    }


    private Request convertToOkHttpRequest(HttpRequest httpRequest) {
        String url = httpRequest.getUrl();
        if (StringUtils.isAllBlank(url)) {
            throw new RuntimeException(ConstantsErrors.INVALID_URL);
        }
        if (StringUtils.isAllBlank(httpRequest.getHttpMethod())) {
            throw new RuntimeException(ConstantsErrors.HTTP_METHOD_BLANK);
        }
        if (HttpMethods.findByValue(httpRequest.getHttpMethod()) == null) {
            throw new RuntimeException(ConstantsErrors.HTTP_METHOD_INVALID);
        }
        if (MapUtils.isNotEmpty(httpRequest.getUrlParams())) {
            url = substituteUrlParams(httpRequest.getUrl(), httpRequest.getUrlParams());
        }
        if (MapUtils.isNotEmpty(httpRequest.getQueryParams())) {
            url = addQueryParams(url, httpRequest.getQueryParams());
        }
        return buildRequest(url, httpRequest.getRequestBody(), httpRequest.getHttpMethod(), httpRequest.getHeaders(), httpRequest.getContentType());

    }

    private Request buildRequest(String url, @Nullable String requestBody, String httpMethod, @Nullable Map<String, String> headers, @Nullable String contentType) {
        HttpMethods method = HttpMethods.findByValue(httpMethod);
        if (method == null) {
            throw new RuntimeException(ConstantsErrors.HTTP_METHOD_INVALID);
        }
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        if (headers != null) {
            builder.headers(Headers.of(headers));
        }

        boolean sendsBody = method == HttpMethods.POST || method == HttpMethods.PUT || method == HttpMethods.PATCH
                || ((method == HttpMethods.DELETE || method == HttpMethods.OPTIONS) && requestBody != null);
        RequestBody body = null;
        if (sendsBody) {
            String effectiveContentType = contentType != null ? contentType : headerValue(headers, "Content-Type");
            if (effectiveContentType == null && requestBody != null && GenericUtils.isJSONValid(requestBody)) {
                effectiveContentType = "application/json";
            }
            if (effectiveContentType != null) {
                // header(), not addHeader(): a Content-Type already in `headers` is replaced, never sent twice.
                builder.header("Content-Type", effectiveContentType);
            }
            body = this.createRequestBody(requestBody, effectiveContentType);
        }

        switch (method) {
            case POST:
                builder.post(body);
                break;
            case PUT:
                builder.put(body);
                break;
            case PATCH:
                builder.patch(body);
                break;
            case DELETE:
                if (body == null) {
                    builder.delete();
                } else {
                    builder.delete(body);
                }
                break;
            case HEAD:
                builder.head();
                break;
            case OPTIONS:
                builder.method(HttpMethods.OPTIONS.getValue(), body);
                break;
            case GET:
            default:
                builder.get();
                break;
        }
        return builder.build();
    }

    @Nullable
    private static String headerValue(@Nullable Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getOAuth2Token(String url, Map<String, String> formBodyPairs, String jsonPathExtractor) {
        if (MapUtils.isEmpty(formBodyPairs)) {
            return null;
        }
        FormBody.Builder requestBodyBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formBodyPairs.entrySet()) {
            requestBodyBuilder.add(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
                .url(url)
                .method(HttpMethods.POST.getValue(), requestBodyBuilder.build())
                .build();
        try (Response response = this.sharedOkHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                return JsonPath.read(body, jsonPathExtractor);
            }
            // Say which failure this was: a rejected client secret and an unreachable server both
            // return null to the caller, and only the log can tell them apart.
            logger.error("OAuth2 token request to {} was rejected with HTTP {}", url, response.code());
        } catch (IOException e) {
            logger.error("Failed to get OAuth2 token from {}: {}", url, e.getMessage(), e);
        }
        return null;
    }

    /**
     * Registers any OkHttp interceptor to an existing client
     * @param okHttpClient The existing client
     * @param interceptor The interceptor to register
     * @return A new OkHttpClient with the interceptor added
     */
    public OkHttpClient registerInterceptor(OkHttpClient okHttpClient, Interceptor interceptor) {
        return okHttpClient.newBuilder()
                .addInterceptor(interceptor)
                .build();
    }

}
