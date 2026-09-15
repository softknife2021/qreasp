package com.softknife.integraton.tc.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softknife.integraton.tc.client.model.TCRequests;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.rest.client.RestClientHelper;
import com.softknife.rest.model.HttpRequest;
import com.softknife.util.common.RBFileUtils;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

/**
 * @author smatsaylo
 * @project tc-client
 */
public class TeamCityClient {

    private String serverUrl;
    private OkHttpClient tcClient;
    private ObjectMapper objectMapper;
    private TCRequests tcRequests;
    private String jsonTcRequests;
    private static final String REQUESTS_FILE = "tc-http-requests.json";
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public TeamCityClient(String serverUrl, String authToken) throws Exception {
        if(StringUtils.isBlank(serverUrl)){
            throw new IllegalArgumentException("Server url must be provided");
        }
        if(StringUtils.isBlank(authToken)){
            throw new IllegalArgumentException("Auth token must be provided");
        }
        this.serverUrl = serverUrl.replaceAll("/$", "");
        this.tcClient = RestClientHelper.getInstance().buildBearerClient(authToken, getHeaders());
        this.objectMapper = GlobalResourceManager.getInstance().getObjectMapper();
        this.jsonTcRequests = RBFileUtils.getFileOnClassPathAsString(REQUESTS_FILE);
        this.tcRequests = objectMapper.readValue(jsonTcRequests, TCRequests.class);
        this.initServerUrl(this.serverUrl);
    }

    /** The caller owns the returned response and must close it. Null when no response came back. */
    public Response getBuilds() {
        return executeCall(HttpRequest.copyOf(this.tcRequests.getGetBuilds()));
    }

    public Response getBuildById(String buildId){
        Map<String, String> urlParams = new HashMap<>();
        urlParams.put("id", buildId);
        HttpRequest httpRequest = HttpRequest.copyOf(this.tcRequests.getGetBuildById());
        httpRequest.setUrlParams(urlParams);
        return executeCall(httpRequest);
    }

    public Response postBuild(String jsonRequestBody) {
        HttpRequest httpRequest = HttpRequest.copyOf(this.tcRequests.getPostBuild());
        httpRequest.setRequestBody(jsonRequestBody);
        return executeCall(httpRequest);
    }

    private Response executeCall(HttpRequest httpRequest){
        try {
            return RestClientHelper.getInstance().executeRequest(tcClient, httpRequest);
        } catch (IOException e) {
            logger.error("TeamCity request {} {} failed: {}", httpRequest.getHttpMethod(), httpRequest.getUrl(), e.getMessage());
        }
        return null;
    }

    private Map<String, String> getHeaders(){
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        return headers;
    }

    public void initServerUrl(String serverUrl) {
        Map<String, HttpRequest> restRequestMap;
        restRequestMap =
                objectMapper.convertValue(
                        tcRequests, new TypeReference<Map<String, HttpRequest>>() {});
        for (Map.Entry<String, HttpRequest> entry : restRequestMap.entrySet()) {
            entry.getValue().setUrl(serverUrl + entry.getValue().getUri());
        }
        try {
            this.jsonTcRequests = this.objectMapper.writeValueAsString(restRequestMap);
            this.tcRequests = objectMapper.readValue(jsonTcRequests, TCRequests.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not apply server URL to the TeamCity request templates: " + e.getMessage(), e);
        }
    }

}
