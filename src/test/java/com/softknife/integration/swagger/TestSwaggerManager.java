package com.softknife.integration.swagger;

import com.softknife.exception.RecordNotFound;
import com.softknife.integraton.swagger.OpenApiV3Manager;
import com.softknife.integraton.swagger.SwaggerApiResourceFilter;
import com.softknife.integraton.swagger.SwaggerDescriptorHelper;
import com.softknife.integraton.swagger.SwaggerManager;
import com.softknife.integraton.swagger.model.SwaggerApiResource;
import com.softknife.integraton.swagger.model.SwaggerDescriptor;
import com.softknife.util.common.RBFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sasha Matsaylo on 2020-10-02
 * @project qreasp
 */
public class TestSwaggerManager {

    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String swaggerUrl = "https://petstore.swagger.io/v2/swagger.json";
    private final String openApiUrl = "https://petstore3.swagger.io/api/v3/openapi.json";
    private List<String> swaggerUrls;
    private List<SwaggerDescriptor> swaggerDescriptors;
    private final int swaggerSize = 3;
    private String jsonContent;


    @BeforeClass
    private void setUp() {
        swaggerUrls = new ArrayList<>();
        this.swaggerDescriptors = new ArrayList<>();
        this.setUrls();
        this.jsonContent = RBFileUtils.getFileOnClassPathAsString("swagger/open-api.json");
    }

    private void setUrls(){
        for(int i=1; i<swaggerSize; i++){
            this.swaggerUrls.add(this.swaggerUrl);
        }
    }


    @Test()
    public void build_swagger_resource(){
        Assert.assertNotNull(SwaggerManager.getInstance().getSwaggerDescriptor(swaggerUrl));
    }

    @Test()
    public void build_open_api_resource_from_url(){
        Assert.assertNotNull(OpenApiV3Manager.getInstance().getSwaggerDescriptorFromUrl(openApiUrl));
    }

    @Test(enabled = true)
    public void build_swagger_descriptor(){
        SwaggerDescriptor swaggerDescriptor = SwaggerManager.getInstance().getSwaggerDescriptor(swaggerUrl);
        Assert.assertNotNull(swaggerDescriptor);
        this.swaggerDescriptors.add(swaggerDescriptor);
    }

    @Test(enabled = true)
    public void build_open_api_descriptor(){
        SwaggerDescriptor swaggerDescriptor = OpenApiV3Manager.getInstance().getSwaggerDescriptorFromUrl(openApiUrl);
        Assert.assertNotNull(swaggerDescriptor);
        this.swaggerDescriptors.add(swaggerDescriptor);

    }

    @Test(enabled = true)
    public void build_swagger_from_list(){
        List<SwaggerDescriptor> swaggerDescriptor = SwaggerManager.getInstance().getSwaggerApiResources(this.swaggerUrls);
        Assert.assertNotNull(swaggerDescriptor);
        Assert.assertTrue(swaggerDescriptor.size() == this.swaggerSize - 1);

    }

    @Test(enabled = true, dependsOnMethods = "build_open_api_descriptor")
    public void build_swagger_filter() throws RecordNotFound {
        SwaggerApiResource swaggerApiResource = SwaggerApiResourceFilter.fetchApiResource(this.swaggerDescriptors, "Swagger Petstore - OpenAPI 3.0", "addPet");
        Assert.assertNotNull(swaggerApiResource);
    }

    @Test(enabled = true)
    public void build_swagger_from_json_content(){
        SwaggerDescriptor swaggerDescriptor = SwaggerManager.getInstance().getSwaggerDescriptorFromSwaggerContent(this.jsonContent);
        Assert.assertNotNull(swaggerDescriptor);
    }

    @Test(enabled = true)
    public void build_descriptor_with_swagger_helper(){
        SwaggerDescriptorHelper.getInstance().initNoneAuthSwaggers(this.swaggerUrls);
        Assert.assertTrue(SwaggerDescriptorHelper.getInstance().getSwaggerDescriptor().size() == this.swaggerUrls.size());
    }

    @Test(enabled = true)
    public void swagger_helper_search_api_resource() throws RecordNotFound {
        SwaggerDescriptorHelper.getInstance().initNoneAuthSwaggers(this.swaggerUrls);
        Assert.assertNotNull(SwaggerDescriptorHelper.getInstance().fetchApiResource("Swagger Petstore", "Finds Pets by status"));
    }





}
