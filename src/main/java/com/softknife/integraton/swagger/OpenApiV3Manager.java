package com.softknife.integraton.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.softknife.integraton.swagger.model.OpenApiParseException;
import com.softknife.integraton.swagger.model.SwaggerApiResource;
import com.softknife.integraton.swagger.model.SwaggerDescriptor;
import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.oas.inflector.processors.JsonNodeExampleSerializer;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * @author Sasha matsaylo on 2020-09-10
 * @project qreasp
 */
public class OpenApiV3Manager {

    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Serialises generated request-body examples. A private copy: registering the example serializer
     * on the global {@code Json.mapper()} on every call changed swagger's shared mapper for everyone.
     */
    private static final ObjectMapper EXAMPLE_MAPPER =
            Json.mapper().copy().registerModule(new SimpleModule().addSerializer(new JsonNodeExampleSerializer()));


    private OpenApiV3Manager() {
    }

    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final OpenApiV3Manager INSTANCE = new OpenApiV3Manager();
    }

    public static OpenApiV3Manager getInstance() {
        return Holder.INSTANCE;
    }

    private Map<String, Schema> getSchemas(OpenAPI openAPI) {
        if (openAPI.getComponents() != null) {
            return openAPI.getComponents().getSchemas();
        }
        return null;
    }

    private SwaggerApiResource setSwaggerApiResource(Map<String, Schema> schemas, SwaggerApiResource swaggerApiResource, Operation operation){
        if(schemas != null){
            if(operation.getRequestBody() != null){
                String requestBody = buildRequestBody(schemas, operation);
                if(requestBody != null){
                    swaggerApiResource.setBody(requestBody);
                }
            }
            else {
                logger.debug("Request body not set for {} {}", swaggerApiResource.getHttpMethod(), swaggerApiResource.getResourcePath());
                swaggerApiResource.setBody("Body has not been set");
            }
        }
        return swaggerApiResource;
    }

    public SwaggerDescriptor getSwaggerDescriptorFromUrl(String swaggerUrl) throws OpenApiParseException {
        OpenAPI openAPI = new OpenAPIV3Parser().read(swaggerUrl);
        if (openAPI == null) {
            throw new OpenApiParseException("Failed to build openapi from URL: " + swaggerUrl);
        }
        return buildDescriptor(openAPI);
    }

    public SwaggerDescriptor getSwaggerDescriptor(String swaggerContent) throws OpenApiParseException {
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(swaggerContent, null, null).getOpenAPI();
        if (openAPI == null) {
            throw new OpenApiParseException("Failed to build openapi from content");
        }
        return buildDescriptor(openAPI);
    }

    private SwaggerDescriptor buildDescriptor(OpenAPI openAPI) {
        SwaggerDescriptor swaggerDescriptor = new SwaggerDescriptor();
        if (openAPI.getInfo() != null) {
            swaggerDescriptor.setApiTitle(openAPI.getInfo().getTitle());
        }
        Map<String, Schema> schemas = getSchemas(openAPI);
        swaggerDescriptor.setServerUrl(OpenApiResources.serverUrl(openAPI));
        swaggerDescriptor.setSwaggerApiResources(
                OpenApiResources.build(openAPI, (operation, resource) -> setSwaggerApiResource(schemas, resource, operation)));
        return swaggerDescriptor;
    }

    /**
     * Descriptors for a list of spec URLs, fetched in parallel. A URL that fails is logged and skipped.
     */
    public List<SwaggerDescriptor> getSwaggerApiResources(List<String> swaggerUrls) {
        List<SwaggerDescriptor> swaggerDescriptors = Collections.synchronizedList(new ArrayList<>());
        swaggerUrls.stream().parallel().forEach(url -> {
            try {
                // From the URL. This called the content parser with the URL string as the content,
                // so every entry failed to parse and the list always came back empty.
                SwaggerDescriptor descriptor = getSwaggerDescriptorFromUrl(url);
                if (descriptor != null) {
                    swaggerDescriptors.add(descriptor);
                }
            } catch (Exception e) {
                logger.error("Failed to obtain swagger resource for url: {}", url, e);
            }
        });
        return swaggerDescriptors;
    }

    private String buildRequestBody(Map<String, Schema> schemas, Operation operation) {
        if (operation.getRequestBody() == null) {
            return null;
        }
        Schema model = buildSchema(schemas, operation);
        if (model == null) {
            logger.debug("No schema found for request body, using empty placeholder");
            return null;
        }
        Example example = ExampleBuilder.fromSchema(model, schemas);
        try {
            return EXAMPLE_MAPPER.writeValueAsString(example);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize request body example: {}", e.getMessage(), e);
            return null;
        }
    }

    private Schema buildSchema(Map<String, Schema> schemas, Operation operation){
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody.get$ref() != null) {
            return schemas.get(refName(requestBody.get$ref()));
        }
        Content content = requestBody.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        MediaType first = content.values().iterator().next();
        if (first == null || first.getSchema() == null || first.getSchema().get$ref() == null) {
            return null;
        }
        return schemas.get(refName(first.getSchema().get$ref()));
    }

    /** The component name a {@code $ref} points at: the part after the last '/', whatever the depth. */
    static String refName(String ref) {
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }
}
