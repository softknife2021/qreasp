package com.softknife.integraton.swagger;

import com.softknife.integraton.swagger.model.OperationParameters;
import com.softknife.integraton.swagger.model.SwaggerApiResource;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Walks an OpenAPI document into {@link SwaggerApiResource}s.
 *
 * <p>The one implementation shared by {@link SwaggerManager} and {@link OpenApiV3Manager}, which
 * each carried an identical copy of this walk.
 */
final class OpenApiResources {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /** The operations collected, in the order they are reported. */
    private static final HttpMethod[] COLLECTED = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE};

    private OpenApiResources() {
    }

    static String serverUrl(OpenAPI openAPI) {
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            return openAPI.getServers().get(0).getUrl();
        }
        logger.warn("No servers defined in OpenAPI spec, using empty string");
        return "";
    }

    /**
     * One resource per operation.
     *
     * @param enrich applied to each resource after it is built, with its operation — the V3 manager
     *               uses it to add an example request body
     */
    static List<SwaggerApiResource> build(OpenAPI openAPI, BiFunction<Operation, SwaggerApiResource, SwaggerApiResource> enrich) {
        List<SwaggerApiResource> resources = new ArrayList<>();
        Paths paths = openAPI.getPaths();
        if (paths == null) {
            logger.warn("OpenAPI spec defines no paths");
            return resources;
        }
        String serverUrl = serverUrl(openAPI);
        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            Map<HttpMethod, Operation> operations = entry.getValue().readOperationsMap();
            String resourcePath = serverUrl + entry.getKey();
            for (HttpMethod method : COLLECTED) {
                Operation operation = operations.get(method);
                if (operation != null) {
                    SwaggerApiResource resource = create(operation, resourcePath, method.name());
                    resource = SwaggerDescriptorHelper.normalizeSwaggerApiResource(resource, serverUrl);
                    resources.add(enrich.apply(operation, resource));
                }
            }
        }
        return resources;
    }

    private static SwaggerApiResource create(Operation operation, String resourcePath, String httpVerb) {
        SwaggerApiResource apiResource = new SwaggerApiResource();
        apiResource.setResourcePath(resourcePath);
        apiResource.setHttpMethod(httpVerb);
        if (operation.getOperationId() != null) {
            apiResource.setOperationId(operation.getOperationId());
        }
        if (operation.getParameters() != null) {
            apiResource.setOperationParameters(parameters(operation.getParameters()));
        }
        if (StringUtils.isNotEmpty(operation.getSummary())) {
            apiResource.setSummary(operation.getSummary());
        }
        if (StringUtils.isNotEmpty(operation.getDescription())) {
            apiResource.setDescription(operation.getDescription());
        }
        return apiResource;
    }

    private static List<OperationParameters> parameters(List<Parameter> parameters) {
        List<OperationParameters> operationParametersList = new ArrayList<>();
        for (Parameter parameter : parameters) {
            OperationParameters operationParameters = new OperationParameters();
            operationParameters.setDescription(parameter.getDescription());
            operationParameters.setIn(parameter.getIn());
            operationParameters.setName(parameter.getName());
            operationParameters.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
            operationParametersList.add(operationParameters);
        }
        return operationParametersList;
    }
}
