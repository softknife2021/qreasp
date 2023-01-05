package com.softknife.data.templating;

import com.softknife.resource.GlobalResourceManager;
import com.softknife.util.common.Constant;
import com.softknife.util.common.GenericUtils;
import com.softknife.util.common.RBFileUtils;
import freemarker.cache.StringTemplateLoader;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateHashModel;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Sasha Matsaylo on 8/7/21
 * @project qreasp
 */

public class TemplateLoaderHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private static Template loadTemplate(Configuration freemarkerConfig, String templateName, String templatePath) {
        try {
            String templateContent = new String(Files.readAllBytes(Paths.get(templatePath)));
            ((StringTemplateLoader) freemarkerConfig.getTemplateLoader()).putTemplate(templateName, templateContent);
            return freemarkerConfig.getTemplate(templateName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Template convertToTemplate(Configuration freemarkerConfig, String templateName, String templateAsString) {
        try {
            ((StringTemplateLoader) freemarkerConfig.getTemplateLoader()).putTemplate(templateName, templateAsString);
            return freemarkerConfig.getTemplate(templateName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static String processTemplate(Configuration freemarkerConfig, String templateDirectory, String templateName, Map<String, Object> data) {
        Template template = loadTemplate(freemarkerConfig, templateName, templateDirectory + templateName);
        try (StringWriter writer = new StringWriter()) {
            template.process(data, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String processTemplate(Configuration freemarkerConfig, TemplateHolder templateHolder, Map<String, Object> data) throws IOException, TemplateException {
        Template template = convertToTemplate(freemarkerConfig, templateHolder.getName(), templateHolder.getTemplate());
        StringWriter writer = new StringWriter();
        template.process(data, writer);
        return writer.toString();
    }

    public static List<TemplateHolder> bulkTemplateLoader(String startDir, String[] extensions, boolean isRecursive, String metaDataSplitter, String metaDataKeyValueSplitter) {
        List<File> result = RBFileUtils.getInstance().readAllFiles(startDir, extensions, isRecursive);
        List<TemplateHolder> templates = new ArrayList<>();
        result.stream().forEach(file -> {
            try {
                if (file.getName().contains(".ftl")) {
                    TemplateHolder templateHolder = new TemplateHolder();
                    String template = FileUtils.readFileToString(file);
                    String templateMetaData = GenericUtils.RegexMatcher(template, Constant.TEMPLATE_METADATA_REGEX.toString(), 1);
                    Map<String, String> templateMap = new HashMap<>(GenericUtils.splitToMap(metaDataSplitter, metaDataKeyValueSplitter, templateMetaData));
                    templateMap.put("template", template);
                    templateHolder = GlobalResourceManager.getInstance().getObjectMapper().convertValue(templateMap, TemplateHolder.class);
                    Optional<String> input = findJsonInputFile(result, templateHolder.getInputFileName());
                    if (input.isPresent()) {
                        templateHolder.setInput(input.get());
                    }
                    templates.add(templateHolder);
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return templates;
    }

    private static Optional<String> findJsonInputFile(List<File> fileList, String fileName) throws IOException {

        Optional<File> optionalFile = fileList.stream()
                .filter(file -> file.getName().equalsIgnoreCase(fileName))
                .findFirst();
        if (!optionalFile.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(FileUtils.readFileToString(optionalFile.get(), CharEncoding.UTF_8));
    }

    public static Configuration getFreeMarkerConfig() {
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_23);
        freemarkerConfig.setTagSyntax(Configuration.ANGLE_BRACKET_TAG_SYNTAX);
        freemarkerConfig.setDefaultEncoding(CharEncoding.UTF_8);
        freemarkerConfig.setNumberFormat("computer");
        freemarkerConfig.setObjectWrapper(new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build());
        freemarkerConfig.setTemplateLoader(new StringTemplateLoader());
        return freemarkerConfig;
    }

    public static String jsonToJson(Configuration freemarkerConfig, TemplateHolder templateHolder) throws TemplateException, IOException {
        String output = null;

        Map<String, Object> data = new HashMap<>();
        data.put("input", templateHolder.getInput());
        TemplateHashModel staticModels = new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build().getStaticModels();
        data.put("JsonUtil", staticModels.get(JsonTemplateMapper.class.getName()));
        output = processTemplate(freemarkerConfig, templateHolder, data);
        return output;
    }
}



