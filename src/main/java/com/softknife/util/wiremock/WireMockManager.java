package com.softknife.util.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.jayway.jsonpath.JsonPath;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.rest.client.HttpMethods;
import com.softknife.rest.client.RestClientHelper;
import com.softknife.rest.model.HttpRequest;
import net.minidev.json.JSONArray;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * A process-wide WireMock server loaded with a set of stubs.
 *
 * <p>Asking for the instance with different stubs replaces the loaded stubs, and asking with a
 * different port restarts the server on that port. It used to return the first instance
 * unchanged, so a second stub set was silently ignored.
 *
 * @author softknife on 10/15/18
 * @project qreasp
 */
public class WireMockManager {

    public static final int DEFAULT_PORT = 8090;

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Object LOCK = new Object();
    private static WireMockManager instance;

    private final int wireMockPort;
    private final String wireMockAdminUrl;
    private final OkHttpClient wireMockClient = RestClientHelper.getInstance().buildNoAuthClient();
    private WireMockServer wireMockServer;
    private String jsonWireMockStubs;


    private WireMockManager(String listOfWireMockStubsAsJson, int port) throws IOException {
        this.wireMockPort = port;
        this.wireMockAdminUrl = "http://localhost:" + port + "/__admin/mappings";
        this.jsonWireMockStubs = listOfWireMockStubsAsJson;
        this.wireMockSetInitialState();
    }


    public static WireMockManager getInstance(String listOfWireMockStubsAsJson) throws IOException {
        return getInstance(listOfWireMockStubsAsJson, DEFAULT_PORT);
    }

    public static WireMockManager getInstance(String listOfWireMockStubsAsJson, int port) throws IOException {
        synchronized (LOCK) {
            if (instance == null || !instance.isRunning() || instance.wireMockPort != port) {
                if (instance != null) {
                    instance.stopWireMock();
                }
                instance = new WireMockManager(listOfWireMockStubsAsJson, port);
            } else if (!Objects.equals(instance.jsonWireMockStubs, listOfWireMockStubsAsJson)) {
                logger.info("WireMock on port {} is already running with different stubs; replacing them", port);
                instance.replaceStubs(listOfWireMockStubsAsJson);
            }
            return instance;
        }
    }

    /**
     * Check if the WireMock server is running.
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return wireMockServer != null && wireMockServer.isRunning();
    }

    public int getPort() {
        return wireMockPort;
    }

    public void startWireMock() {
        this.wireMockServer.start();
    }

    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            this.wireMockServer.stop();
        }
    }

    public void resetWireMock() {
        this.wireMockServer.resetAll();
    }

    public void resetScenarios(){
        this.wireMockServer.resetScenarios();
    }

    public List<StubMapping> getWireMockStubs(){
        return this.wireMockServer.getStubMappings();
    }

    private void replaceStubs(String stubs) throws IOException {
        this.wireMockServer.resetAll();
        this.jsonWireMockStubs = stubs;
        loadStubs();
    }

    private void wireMockSetInitialState() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort));
        startWireMock();
        waitForWireMockReady();
        loadStubs();
    }

    private void loadStubs() throws IOException {
        JSONArray jsonArray = JsonPath.read(this.jsonWireMockStubs, "$");
        for (Object stub : jsonArray) {
            String jsonStub = GlobalResourceManager.getInstance().getObjectMapper().writeValueAsString(stub);
            HttpRequest httpRequest = new HttpRequest(HttpMethods.POST.getValue(), wireMockAdminUrl);
            httpRequest.setRequestBody(jsonStub);
            try (Response response = RestClientHelper.getInstance().executeRequest(wireMockClient, httpRequest)) {
                if (!response.isSuccessful()) {
                    logger.error("Failed to register WireMock stub (HTTP {}): {}", response.code(), jsonStub);
                }
            }
        }
    }

    private void waitForWireMockReady() {
        int maxRetries = 10;
        int retryDelayMs = 100;
        for (int i = 0; i < maxRetries; i++) {
            HttpRequest healthCheck = new HttpRequest(HttpMethods.GET.getValue(), wireMockAdminUrl);
            try (Response response = RestClientHelper.getInstance().executeRequest(wireMockClient, healthCheck)) {
                if (response.isSuccessful()) {
                    logger.debug("WireMock is ready after {} attempts", i + 1);
                    return;
                }
            } catch (Exception e) {
                logger.debug("WireMock not ready yet, attempt {}/{}", i + 1, maxRetries);
            }
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.warn("WireMock may not be fully ready after {} attempts", maxRetries);
    }
}
