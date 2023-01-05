package com.softknife.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.rest.client.ConstantsErrors;
import com.softknife.rest.client.HttpMethods;
import com.softknife.rest.client.RestClientHelper;
import com.softknife.rest.model.HttpRestRequest;
import com.softknife.util.common.RBFileUtils;
import com.softknife.util.wiremock.WireMockManager;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sasha Matsaylo on 9/17/19
 * @project qreasp
 */
public class TestRestHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String userName = "test";
    private final String password = "password";
    private final Map<String, String> headers = new HashMap<>();
    private final String requestBody = "{\\\"key\\\": \\\"value\\\"}";
    private final ObjectMapper objectMapper = GlobalResourceManager.getInstance().getObjectMapper();
    private OkHttpClient okHttpClient;
    private final String commonUrl = "https://httpbin.org/anything";
    private final String headerKey = "Headerkey";
    private final String headerValue = "headerValue";
    private WireMockManager wireMockManager;

    @BeforeClass
    public void setUp() throws IOException {
        this.headers.put(this.headerKey, this.headerValue);
        String wireMockJsonStubs = RBFileUtils.getFileOnClassPathAsString("wiremock/wiremock-stubs.json");
        this.wireMockManager = WireMockManager.getInstance(wireMockJsonStubs);
        this.okHttpClient = RestClientHelper.getInstance().buildBasicAuthClient(userName, password);
    }


    @Test
    public void testCreate2NewRestClient() {
        Map<String, String> headers = new HashMap<>();
        headers.put("headerName", "headerValue");
        RestClientHelper.getInstance().registerLoggerInterceptorForSharedClient();
        OkHttpClient okHttpClient1 = this.okHttpClient;
        OkHttpClient okHttpClient2 = RestClientHelper.getInstance().buildBasicAuthClient("myUser", "mypassword", headers);
        Assert.assertFalse(okHttpClient1.equals(okHttpClient2));
    }


    @Test//(threadPoolSize = 3, invocationCount = 6)
    public void testDoGetRequest() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.GET.getValue(), this.commonUrl);
        Response response = RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
        Assert.assertTrue(response.code() == 200);

    }


    @Test
    public void testDoPostRequestWithObject() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.POST.getValue(), this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        httpRestRequest.setContentType("application/xml");
        Response response = RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
        Assert.assertTrue(response.code() == 200);
    }

    @Test
    public void verifyHeadersSetOnBearerClientCreation() throws Exception {
        OkHttpClient bearerClient = RestClientHelper.getInstance().buildBearerClient("token", this.headers);
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.POST.getValue(), this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        httpRestRequest.setContentType("application/json");
        Response response = RestClientHelper.getInstance().executeRequest(bearerClient, httpRestRequest);
        String respBody = response.body().string();
        Assert.assertTrue(response.code() == 200);
        String actualHeaderValue = JsonPath.read(respBody, "$.headers." + this.headerKey);
        Assert.assertEquals(this.headerValue, actualHeaderValue);

    }

    //TODO: needs to be fixed headers are not set on request if provided
    @Test(enabled = false)
    public void verifyHeadersSetOnRequestAfterClientIsCreated() throws Exception {
        String headerKey = "jenkins";
        String headerValue = "headerValue";
        OkHttpClient basicAuthClient = RestClientHelper.getInstance().buildBasicAuthClient("user", "password");
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.POST.getValue(), this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        Map<String,String> headersForRequest = new HashMap<>();
        headersForRequest.put(headerKey, headerValue);
        httpRestRequest.setHeaders(headersForRequest);
        Response response = RestClientHelper.getInstance().executeRequest(basicAuthClient, httpRestRequest);
        String respBody = response.body().string();
        Assert.assertTrue(response.code() == 200);
        String actualHeaderValue = JsonPath.read(respBody, "$.headers." + this.headerKey);
        Assert.assertEquals(this.headerValue, actualHeaderValue);

    }

    @Test( expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ConstantsErrors.INVALID_URL)
    public void testDoPostRequestWithObjectNoUrl() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.POST.getValue(), "");
        httpRestRequest.setUrl("");
        httpRestRequest.setHttpMethod(HttpMethods.POST.getValue());
        httpRestRequest.setRequestBody(this.requestBody);
        httpRestRequest.setContentType("application/xml");
        RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
    }

    @Test( expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ConstantsErrors.HTTP_METHOD_INVALID)
    public void testDoPostRequestWithObjectInvalidHttpMethod() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest("Invalid", this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        httpRestRequest.setContentType("application/xml");
        RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
    }

    @Test( expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ConstantsErrors.HTTP_METHOD_BLANK)
    public void testDoPostRequestWithObjectAndNullHttpMethod() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(null, this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        httpRestRequest.setContentType("application/xml");
        RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
    }

    @Test( expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ConstantsErrors.HTTP_METHOD_BLANK)
    public void testDoPostRequestWithObjectAndBlankHttpMethod() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest("", this.commonUrl);
        httpRestRequest.setUrl(this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        httpRestRequest.setContentType("application/xml");
        RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
    }

    @Test
    public void testDoPutRequest() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.PUT.getValue(), this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        Response response = RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
        Assert.assertTrue(response.code() == 200);
    }


    @Test
    public void testDoPatchRequest() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.PATCH.getValue(), this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        Response response = RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
        Assert.assertTrue(response.code() == 200);
    }

    @Test
    public void testDoDeleteRequestWithRequestBody() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.DELETE.getValue(), this.commonUrl);
        httpRestRequest.setRequestBody(this.requestBody);
        Response response = RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
        Assert.assertTrue(response.code() == 200);
    }

    @Test
    public void testDoDeleteRequestWithNoBody() throws IOException {
        HttpRestRequest httpRestRequest = new HttpRestRequest(HttpMethods.DELETE.getValue(), this.commonUrl);
        Response response = RestClientHelper.getInstance().executeRequest(okHttpClient, httpRestRequest);
        Assert.assertTrue(response.code() == 200);
    }

    @Test
    public void buildUrlWithQueryParams() {
        String url = "http://test/search";
        String expectedUrl = "http://test/search?test2=t%26%3F&test=t%2Ftkljl";
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("test", "t/tkljl");
        queryParams.put("test2", "t&?");
        String actualUrl = RestClientHelper.getInstance().addQueryParams(url, queryParams);
        Assert.assertEquals(actualUrl, expectedUrl, "url must match");
    }


    @Test(enabled = true)
    public void get_oath2_token(){
        Map<String,String> params = new HashMap<>();
        params.put("param1", "paramValue1");
        params.put("param2", "paramValue2");
        params.put("param3", "paramValue3");
        String token = RestClientHelper.getInstance().getOAuth2Token("http://localhost:8090/oauth/token", params, "$.access_token");
        Assert.assertEquals(token, "dummytoken", "tokens should match");
    }


}
