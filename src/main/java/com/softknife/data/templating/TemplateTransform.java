/*
 * *
 *  * Created by SOFTKNIFE on 6/15/21, 2:04 PM
 *  * @author Ed Vayn
 *  * @project qreasp
 *  * Copyright (c) 2021 . All rights reserved.
 *  * Last modified 6/15/21, 2:04 PM
 *
 */

package com.softknife.data.templating;

import com.softknife.data.templating.generation.JsonGenerationTemplateMapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.TemplateHashModel;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TemplateTransform {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TemplateTransform.class);

    private String baseDir = "src/test/resources";
    private String templateDir = "/payload/template/";
    private String dataDir = "/payload/test_data/";
    private String jsonSource = dataDir + "test_cases_exported.json";
    private String templateName = "jsonTestParamTemplate.ftl";

    public String jsonToJson(TemplateLoader templateLoader, String inputName, String tempName) {
        String output = null;
        try {
            String input = new String(Files.readAllBytes(Paths.get(baseDir + inputName)), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> data = new HashMap<>();
            data.put("input", input);
            TemplateHashModel staticModels = new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build().getStaticModels();
            data.put("JsonUtil", staticModels.get(JsonTemplateMapper.class.getName()));
            output = templateLoader.processTemplate(tempName, data);
        }
        catch (Exception e){
            LOGGER.error("Template {} could not be rendered: {}", tempName, e.getMessage(), e);
        }
        return output;
    }

    public void jsonToSplitJson(TemplateLoader templateLoader, String inputName, String tempName) throws Exception {
        String input = new String(Files.readAllBytes(Paths.get(baseDir + inputName)), java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> data = new HashMap<>();
        data.put("input", input);
        TemplateHashModel staticModels = new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build().getStaticModels();
        data.put("JsonUtil", staticModels.get(JsonTemplateMapper.class.getName()));
        String output = templateLoader.processTemplate(tempName, data);
        LOGGER.info("{}", output);
    }

    public String setJsonBody(TemplateLoader templateLoader, String input, String tempName) {
        String output = null;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("input", input);
            TemplateHashModel staticModels = new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build().getStaticModels();
            data.put("JsonUtil", staticModels.get(JsonGenerationTemplateMapper.class.getName()));
            output = templateLoader.processTemplate(tempName, data);
        }
        catch (Exception e){
            LOGGER.error("Template {} could not be rendered: {}", tempName, e.getMessage(), e);
        }
        return output;
    }

    public String setJsonBodyWithFooterAndHeader(TemplateLoader templateLoader, String input,
                                                 String footer, String header, String tempName) {
        String output = null;
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("input", input);
            data.put("footer", footer);
            data.put("header", header);
            TemplateHashModel staticModels = new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build().getStaticModels();
            data.put("JsonUtil", staticModels.get(JsonGenerationTemplateMapper.class.getName()));
            output = templateLoader.processTemplate(tempName, data);
        }
        catch (Exception e){
            LOGGER.error("Template {} could not be rendered: {}", tempName, e.getMessage(), e);
        }
        return output;
    }
    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getTemplateDir() {
        return templateDir;
    }

    public void setTemplateDir(String templateDir) {
        this.templateDir = templateDir;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getJsonSource() {
        return jsonSource;
    }

    public void setJsonSource(String jsonSource) {
        this.jsonSource = jsonSource;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }
}
