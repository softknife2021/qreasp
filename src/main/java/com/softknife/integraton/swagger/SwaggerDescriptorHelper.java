package com.softknife.integraton.swagger;

import com.softknife.exception.RecordNotFound;
import com.softknife.integraton.swagger.model.SwaggerApiResource;
import com.softknife.integraton.swagger.model.SwaggerDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sasha Matsaylo on 8/22/21
 * @project qreasp
 */
public class SwaggerDescriptorHelper {

    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private List<SwaggerDescriptor> swaggerDescriptors = new ArrayList<>();


    public SwaggerDescriptorHelper(){
    }

    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final SwaggerDescriptorHelper INSTANCE = new SwaggerDescriptorHelper();
    }

    public static SwaggerDescriptorHelper getInstance() {
        return Holder.INSTANCE;
    }

    public void initNoneAuthSwaggers(List<String> swaggerUrls){
        for(String url : swaggerUrls){
            SwaggerDescriptor swaggerDescriptor = SwaggerManager.getInstance().getSwaggerDescriptor(url);
            if(swaggerDescriptor != null){
                logger.info("Setting swagger descriptor for url: {}", url);
                this.swaggerDescriptors.add(swaggerDescriptor);
            }
            else {
                logger.error("Failed to build swagger descriptor for url: {}", url);
            }
        }
    }

    /**
     * <p>This is a simple description of the method. . .
     * <a href="https://github.com/softknife2021/qreasp">Build from real world cases!</a>
     * </p>
     * @param apiTitle the value of Swagger title exactly as it appears in swagger
     * @param operationIdOrSummary the operationId or summary exactly as it appears in json schema or swagger ui
     * @return api resource metadata from swagger
     * @since 0.0.32
     */
    public SwaggerApiResource fetchApiResource(String apiTitle, String operationIdOrSummary) throws RecordNotFound {
        if(StringUtils.isBlank(apiTitle)){
            throw new IllegalArgumentException("Invalid argument apiTitle: " + apiTitle);
        }
        if(StringUtils.isBlank(operationIdOrSummary)){
            throw new IllegalArgumentException("Invalid argument operationIdOrSummary: " + operationIdOrSummary);
        }
        return SwaggerApiResourceFilter.fetchApiResource(this.swaggerDescriptors, apiTitle, operationIdOrSummary);
    }

    public void resetSwaggerDescriptor(List<SwaggerDescriptor>swaggerDescriptors){
        this.swaggerDescriptors = swaggerDescriptors;

    }

    public List<SwaggerDescriptor> getSwaggerDescriptor(){
        return this.swaggerDescriptors;
    }

    protected static SwaggerApiResource normalizeSwaggerApiResource(SwaggerApiResource swaggerApiResource, String serverUrl) {
        swaggerApiResource.setPathParams(getNormalizedPathParams(swaggerApiResource.getResourcePath()));
        return swaggerApiResource;
    }

    static List<String> getNormalizedPathParams(String path) {
        List<String> params = new ArrayList<>();
        if (path == null) {
            return params;
        }
        int openBracePos = path.indexOf('{');
        while (openBracePos != -1) {
            int closeBracePos = path.indexOf('}', openBracePos);
            if (closeBracePos == -1) {
                // Stop here. The old loop did not advance on an unclosed '{' and never ended,
                // so one malformed path in a spec hung the JVM.
                logger.warn("Unclosed '{' in resource path, ignoring the rest of it: {}", path);
                break;
            }
            params.add(path.substring(openBracePos + 1, closeBracePos));
            openBracePos = path.indexOf('{', closeBracePos + 1);
        }
        return params;
    }



}
