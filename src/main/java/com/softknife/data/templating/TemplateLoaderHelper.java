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
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Sasha Matsaylo on 8/7/21
 * @project qreasp
 */

public class TemplateLoaderHelper {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    private static Template loadTemplate(Configuration freemarkerConfig, String templateName, String templatePath) {
        try {
            String templateContent = Files.readString(Paths.get(templatePath), StandardCharsets.UTF_8);
            ((StringTemplateLoader) freemarkerConfig.getTemplateLoader()).putTemplate(templateName, templateContent);
            return freemarkerConfig.getTemplate(templateName);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load template " + templatePath, e);
        }
    }

    private static Template convertToTemplate(Configuration freemarkerConfig, String templateName, String templateAsString) {
        try {
            ((StringTemplateLoader) freemarkerConfig.getTemplateLoader()).putTemplate(templateName, templateAsString);
            return freemarkerConfig.getTemplate(templateName);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not compile template " + templateName, e);
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

    /**
     * Every {@code .ftl} file under {@code startDir} that carries a metadata comment, with its JSON input attached.
     * A template without metadata is skipped with a warning naming the file.
     */
    public static List<TemplateHolder> bulkTemplateLoader(String startDir, String[] extensions, boolean isRecursive, String metaDataSplitter, String metaDataKeyValueSplitter) {
        List<File> result = RBFileUtils.getInstance().readAllFiles(startDir, extensions, isRecursive);
        List<TemplateHolder> templates = new ArrayList<>();
        for (File file : result) {
            // endsWith, not contains: "notes.ftl.bak" is not a template.
            if (!file.getName().endsWith(".ftl")) {
                continue;
            }
            try {
                String template = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                String templateMetaData = GenericUtils.regexMatch(template, Constant.TEMPLATE_METADATA_REGEX.toString(), 1);
                if (templateMetaData == null) {
                    logger.warn("Template {} has no metadata comment and was skipped", file);
                    continue;
                }
                Map<String, String> templateMap = new HashMap<>(GenericUtils.splitToMap(metaDataSplitter, metaDataKeyValueSplitter, templateMetaData));
                templateMap.put("template", template);
                TemplateHolder templateHolder = GlobalResourceManager.getInstance().getObjectMapper().convertValue(templateMap, TemplateHolder.class);
                Optional<String> input = findJsonInputFile(result, templateHolder.getInputFileName());
                input.ifPresent(templateHolder::setInput);
                templates.add(templateHolder);
            } catch (IOException e) {
                logger.error("Could not read template {}: {}", file, e.getMessage());
            }
        }
        return templates;
    }

    private static Optional<String> findJsonInputFile(List<File> fileList, String fileName) throws IOException {

        Optional<File> optionalFile = fileList.stream()
                .filter(file -> file.getName().equalsIgnoreCase(fileName))
                .findFirst();
        if (!optionalFile.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(FileUtils.readFileToString(optionalFile.get(), StandardCharsets.UTF_8));
    }

    /**
     * The FreeMarker configuration every template loader in this package uses. One place, so the
     * loaders cannot drift apart in version or settings.
     */
    public static Configuration getFreeMarkerConfig() {
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_23);
        freemarkerConfig.setTagSyntax(Configuration.ANGLE_BRACKET_TAG_SYNTAX);
        freemarkerConfig.setDefaultEncoding(StandardCharsets.UTF_8.name());
        freemarkerConfig.setNumberFormat("computer");
        freemarkerConfig.setObjectWrapper(new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build());
        freemarkerConfig.setTemplateLoader(new StringTemplateLoader());
        return freemarkerConfig;
    }

    public static String jsonToJson(Configuration freemarkerConfig, TemplateHolder templateHolder) throws TemplateException, IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("input", templateHolder.getInput());
        TemplateHashModel staticModels = new BeansWrapperBuilder(Configuration.VERSION_2_3_23).build().getStaticModels();
        data.put("JsonUtil", staticModels.get(JsonTemplateMapper.class.getName()));
        return processTemplate(freemarkerConfig, templateHolder, data);
    }
}
