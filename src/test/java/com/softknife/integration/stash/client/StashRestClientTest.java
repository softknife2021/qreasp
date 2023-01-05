/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.softknife.integration.stash.client;

import com.jayway.jsonpath.JsonPath;
import com.softknife.integraton.stash.client.StashRestClient;
import com.softknife.integraton.stash.client.model.StashResponse;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.util.common.RBFileUtils;
import com.softknife.util.wiremock.WireMockManager;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandles;

public class StashRestClientTest {

    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private StashRestClient stashRestClient;
    private String token;
    private String url;
    private WireMockManager wireMockManager;

    @BeforeClass(alwaysRun = true)
    private void setUp() throws Exception {
        //this.url = System.getenv("TC_AUTH_URL");
        //this.url = "https://api.bitbucket.org";
        this.url = "http://localhost:8090";
        Assert.assertNotNull(url);
        //this.token = System.getenv("TC_AUTH_TOKEN");
        this.token = "dummyToken";
        Assert.assertNotNull(token);
        //this.stashRestClient = new StashRestClient(url, token, "DummyProject", "dummyRepoName");
        //this.stashRestClient = new StashRestClient(url,  "dummyOne", "findDummyOne", "Restb", "m-test-rails");
        this.stashRestClient = new StashRestClient(url, "dummyToken", "myproject", "myrepo");
        String wireMockStubs = RBFileUtils.getFileOnClassPathAsString("wiremock/wiremock-stubs.json");
        this.wireMockManager = WireMockManager.getInstance(wireMockStubs);
    }

    @AfterSuite
    private void tearDown(){
        this.wireMockManager.stopWireMock();
    }


    @Test
    private void getCommitsInRange() throws Exception {
        Response response = stashRestClient.getCommitsInRangeV1("since", "until", 0, 25);
        String body = response.body().string();
        response.close();
        Integer actualResult = JsonPath.read(body, "$.size");
        Assert.assertTrue(actualResult == 2);
        logger.info(GlobalResourceManager.getInstance().getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(body));
    }

    @Test
    private void getTags() throws Exception {
        Response response = stashRestClient.getTagsV1(0, 25);
        String body = response.body().string();
        String actualResult = JsonPath.read(body, "$.values[0].type");
        Assert.assertEquals(actualResult, "TAG");
        logger.info(GlobalResourceManager.getInstance().getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(body));
    }

    @Test
    private void getFileContent() throws Exception {
        StashResponse stashResponse = stashRestClient.getFileContent("/my/file/path/file.txt", "refs/heads/master");
        Assert.assertEquals(stashResponse.getHttpStatus(), 200);
    }

    @Test
    private void getCommitByHash() throws Exception {
        Response response = stashRestClient.getCommitByHash("dummyGitHash");
        Assert.assertEquals(response.code(), 200);
    }

    @Test
    private void twoStashClients() throws Exception {
        StashRestClient stashRestClient = new StashRestClient("http://newclient.com", "newDummyToken", "newProject", "newRepo");
        Response response = stashRestClient.getCommitByHash("dummyGitHash");
        Assert.assertEquals(response.code(), 404);
    }

}