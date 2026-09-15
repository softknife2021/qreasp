package com.softknife.integraton.swagger;

import com.softknife.integraton.swagger.model.OpenApiParseException;
import com.softknife.integraton.swagger.model.SwaggerDescriptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Sasha matsaylo on 2020-09-10
 * @project qreasp
 */
public class SwaggerManager {

    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private SwaggerManager() {
    }

    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final SwaggerManager INSTANCE = new SwaggerManager();
    }

    public static SwaggerManager getInstance() {
        return Holder.INSTANCE;
    }

    public SwaggerDescriptor getSwaggerDescriptor(String url) {
        OpenAPI openAPI = null;
        try {
            openAPI = initOpenApi(url, "HTTP");
        } catch (OpenApiParseException e) {
            logger.error("OpenApiParseException: {}", e.getMessage(), e);
        }
        return buildDescriptor(openAPI);
    }


    public SwaggerDescriptor getSwaggerDescriptorFromSwaggerContent(String swaggerContent) {
        OpenAPI openAPI = null;
        try {
            openAPI = initOpenApi(swaggerContent, "JSON");
        } catch (OpenApiParseException e) {
            logger.error("OpenApiParseException: {}", e.getMessage(), e);
        }
        return buildDescriptor(openAPI);
    }

    private OpenAPI initOpenApi(String content, String type) throws OpenApiParseException {
        OpenAPI openAPI = null;
        if(type.equalsIgnoreCase("HTTP")){
            openAPI = new OpenAPIV3Parser().read(content);
        }
        if(type.equalsIgnoreCase("JSON")){
            openAPI = new OpenAPIV3Parser().readContents(content, null, null).getOpenAPI();
        }
        if(openAPI == null){
            throw new OpenApiParseException("Failed to build OpenApi");
        }
        return openAPI;
    }

    private SwaggerDescriptor buildDescriptor(OpenAPI openAPI) {
        if (openAPI == null) {
            logger.error("Cannot build descriptor from null OpenAPI");
            return null;
        }
        SwaggerDescriptor swaggerDescriptor = new SwaggerDescriptor();
        if (openAPI.getInfo() != null) {
            swaggerDescriptor.setApiTitle(openAPI.getInfo().getTitle());
        }
        swaggerDescriptor.setServerUrl(OpenApiResources.serverUrl(openAPI));
        swaggerDescriptor.setApiVersion(openAPI.getOpenapi());
        swaggerDescriptor.setSwaggerApiResources(OpenApiResources.build(openAPI, (operation, resource) -> resource));
        return swaggerDescriptor;
    }


    public List<SwaggerDescriptor> getSwaggerApiResources(List<String> swaggerUrls) {
        List<SwaggerDescriptor> swaggerDescriptors = Collections.synchronizedList(new ArrayList<>());
        swaggerUrls.stream().parallel().forEach(url -> {
            try {
                SwaggerDescriptor descriptor = getSwaggerDescriptor(url);
                if (descriptor != null) {
                    swaggerDescriptors.add(descriptor);
                }
            } catch (Exception e) {
                logger.error("Failed to obtain swagger resource for url: {}", url, e);
            }
        });
        return swaggerDescriptors;
    }
}
