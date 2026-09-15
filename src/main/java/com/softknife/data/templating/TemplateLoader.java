/*
 * *
 *  * Created by SOFTKNIFE on 6/15/21, 2:01 PM
 *  * @author Ed Vayn
 *  * @project qreasp
 *  * Copyright (c) 2021 . All rights reserved.
 *  * Last modified 6/15/21, 2:01 PM
 *  * Last modified @author Sasha Matsaylo 6/15/21, 2:01 PM
 *
 */

package com.softknife.data.templating;

import freemarker.template.Configuration;

import java.util.Map;

public class TemplateLoader {

    /** The directory used when none is given; kept for compatibility with existing callers. */
    public static final String DEFAULT_TEMPLATE_DIRECTORY = "src/test/resources/";

    private final Configuration freemarkerConfig;
    private String templateDirectory;

    public TemplateLoader() {
        this(DEFAULT_TEMPLATE_DIRECTORY);
    }

    public TemplateLoader(String templateDirectory) {
        this.templateDirectory = templateDirectory;
        this.freemarkerConfig = TemplateLoaderHelper.getFreeMarkerConfig();
    }

    public String getTemplateDirectory() {
        return templateDirectory;
    }

    public void setTemplateDirectory(String templateDirectory) {
        this.templateDirectory = templateDirectory;
    }

    public String processTemplate(String templateName, Map<String, Object> data) {
        return TemplateLoaderHelper.processTemplate(freemarkerConfig, templateDirectory, templateName, data);
    }

}
