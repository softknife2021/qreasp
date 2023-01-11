package com.softknife.data.templating;

import com.softknife.util.common.RBMapUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * @author Ed Vayn on 6/11/21
 * @project qreasp
 */
public class JsonTemplatingTest {
    private final String baseDir = "src/test/resources";
    private final String templateDir = "/payload/template/";
    private final String dataDir = "/payload/test_data/";
    private final String jsonSource = dataDir + "test_cases_exported.json";
    private final String jsonSource1 = dataDir + "test_pizza_ordering.json";
    private final String jsonSource_for_substitution = dataDir + "data_provider_parameters_tests.json";
    private final String templateSubstituion = "jsonToJSSubstitutionTemplate.ftl";
    private final String templateName = "jsonTestParamTemplate.ftl";
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    TemplateLoader templateLoader = new TemplateLoader();
    TemplateTransform tf = new TemplateTransform();

    @BeforeClass(alwaysRun = true)
    private void setUp() {
        templateLoader.setTemplateDirectory(tf.getBaseDir() + tf.getTemplateDir());
    }

    @Test(description = "Test Json Transfromation with defaults", priority = 4)
    private void testJsonTransfromationWithDefaults() {
        //Transformation from a json to a different json target
        String output = tf.jsonToJson(templateLoader, tf.getJsonSource(), tf.getTemplateName());
        logger.info(output);
        Map<String, Object> outputMap = RBMapUtil.readAsMap(output);
        Assert.assertTrue(output != null, "Generated output must not be null");
        Assert.assertTrue(outputMap != null, "Generated output map must not be null");
        List<Object> testsList = (List<Object>)outputMap.get("tests");
        Assert.assertTrue(testsList.size() > 0, "Tests size must be greater than zero");
        Assert.assertTrue(testsList.size() == 2, "Default Tests size must be equal to 2");
    }

    @Test(description = "Test Json Transfromation For Default template", priority = 5)
    private void testJsonTransfromationForDefaultTemplate() {
        tf.setBaseDir(baseDir);
        tf.setTemplateDir(templateDir);
        tf.setDataDir(dataDir);
        tf.setJsonSource(jsonSource1);
        tf.setTemplateName(templateName);
        //Transformation from a json to a different json target
        String output = tf.jsonToJson(templateLoader, tf.getJsonSource(), tf.getTemplateName());
        Map<String, Object> outputMap = RBMapUtil.readAsMap(output);;
        logger.info(output);
        Assert.assertTrue(output != null, "Generated output must not be null");
        Assert.assertTrue(outputMap != null, "Generated output map must not be null");
        List<Object> testsList = (List<Object>)outputMap.get("tests");
        Assert.assertTrue(testsList.size() > 0, "Tests size must be greater than zero");
        Assert.assertTrue(testsList.size() == 3, "Default Tests size must be equal to 3");
    }

    @Test(description = "Test Json Transformation For Substitution template", priority = 6)
    private void testJsonTransformForSubstitutionTemplate() throws IOException {
        String jsonContent = new String(Files.readAllBytes(Paths.get(baseDir  + "/" + jsonSource_for_substitution)));
        tf.setBaseDir(baseDir);
        tf.setTemplateDir(templateDir);
        tf.setDataDir(dataDir);
        tf.setJsonSource(jsonContent);
        tf.setTemplateName(templateSubstituion);
        String footer = "xyz";
        String header = "abc: abcVal,";
        //Transformation from a json to a different json target
        String output = tf.setJsonBodyWithFooterAndHeader(templateLoader, tf.getJsonSource(), header, footer, tf.getTemplateName());
        //Map<String, Object> outputMap = RBMapUtil.readAsMap(output);;
        logger.info(output);
        Assert.assertTrue(output != null, "Generated output must not be null");
        Assert.assertTrue(StringUtils.isNotBlank(output), "Generated output must not be blank");
        Assert.assertTrue(output.contains(header), "The header must be present");
        Assert.assertTrue(output.contains(footer), "The footer must be present");
        Assert.assertTrue(output.contains(jsonContent), "The content must be present");
        //Assert.assertTrue(outputMap != null, "Generated output map must not be null");
        //List<Object> testsList = (List<Object>)outputMap.get("tests");
        //Assert.assertTrue(testsList.size() > 0, "Tests size must be greater than zero");
        //Assert.assertTrue(testsList.size() == 3, "Default Tests size must be equal to 3");
    }

}
