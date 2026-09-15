package com.softknife.integraton.swagger;

import com.softknife.integraton.swagger.model.SwaggerApiResource;
import com.softknife.integraton.swagger.model.SwaggerDescriptor;
import com.softknife.util.common.RBFileUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * OpenApiV3Manager fixes, against the checked-in petstore fixture served from a local server.
 */
public class OpenApiV3ManagerFixesTest {

    private MockWebServer server;
    private String spec;

    @BeforeMethod
    public void start() throws IOException {
        spec = RBFileUtils.getFileOnClassPathAsString("swagger/open-api.json");
        server = new MockWebServer();
        server.start();
    }

    @AfterMethod(alwaysRun = true)
    public void stop() throws IOException {
        server.shutdown();
    }

    @Test(description = "A list of spec URLs is fetched from the URLs, not parsed as if each URL were a spec")
    public void urlListIsFetched() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(spec));

        List<SwaggerDescriptor> descriptors = OpenApiV3Manager.getInstance()
                .getSwaggerApiResources(List.of(server.url("/openapi.json").toString()));

        assertEquals(descriptors.size(), 1, "the spec URL produced no descriptor");
        assertFalse(descriptors.get(0).getSwaggerApiResources().isEmpty());
    }

    @Test(description = "A request body referenced through content.schema.$ref gets an example body")
    public void contentSchemaRefProducesABody() throws Exception {
        SwaggerDescriptor descriptor = OpenApiV3Manager.getInstance().getSwaggerDescriptor(spec);

        SwaggerApiResource placeOrder = descriptor.getSwaggerApiResources().stream()
                .filter(r -> "POST".equals(r.getHttpMethod()) && r.getResourcePath().endsWith("/store/order"))
                .findFirst().orElseThrow();
        assertNotNull(placeOrder.getBody(), "no example body built for POST /store/order");
        assertTrue(placeOrder.getBody().startsWith("{"), placeOrder.getBody());
    }

    @Test(description = "A $ref name is the part after the last slash, whatever its depth")
    public void refName() {
        assertEquals(OpenApiV3Manager.refName("#/components/schemas/Order"), "Order");
        assertEquals(OpenApiV3Manager.refName("#/a/b/c/d/Deep"), "Deep");
        assertEquals(OpenApiV3Manager.refName("Plain"), "Plain");
    }
}
