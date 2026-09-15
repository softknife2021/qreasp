package com.softknife.integraton.swagger;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Path-parameter extraction from OpenAPI resource paths.
 *
 * <p>Lives in the production package (note the legacy "integraton" spelling) to reach the
 * package-private method directly.
 */
public class SwaggerPathParamsTest {

    @Test(timeOut = 5000, description = "An unclosed brace terminates instead of looping forever")
    public void unclosedBraceTerminates() {
        assertEquals(SwaggerDescriptorHelper.getNormalizedPathParams("/users/{id}/orders/{oid"), List.of("id"));
    }

    @Test(description = "Every closed parameter is found, in order")
    public void closedParametersAreFound() {
        assertEquals(SwaggerDescriptorHelper.getNormalizedPathParams("/a/{x}/b/{y}/c"), List.of("x", "y"));
    }

    @Test(description = "A path without parameters, or a null path, yields nothing")
    public void noParameters() {
        assertTrue(SwaggerDescriptorHelper.getNormalizedPathParams("/plain/path").isEmpty());
        assertTrue(SwaggerDescriptorHelper.getNormalizedPathParams(null).isEmpty());
    }
}
