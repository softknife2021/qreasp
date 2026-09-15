/*
 * *
 *  * Created by SOFTKNIFE on 6/15/21, 2:01 PM
 *  * @author Ed Vayn
 *  * @project qreasp
 *  * Copyright (c) 2021 . All rights reserved.
 *  * Last modified 6/15/21, 2:01 PM
 *
 */

package com.softknife.data.templating;

import java.util.Map;

/**
 * @deprecated identical to {@link TemplateLoader}; use {@code new TemplateLoader(templateDirectory)}.
 */
@Deprecated
public class BulkTemplateLoader {

    private final TemplateLoader delegate;

    public BulkTemplateLoader(String templateDirectory) {
        this.delegate = new TemplateLoader(templateDirectory);
    }

    public String getTemplateDirectory() {
        return delegate.getTemplateDirectory();
    }

    public void setTemplateDirectory(String templateDirectory) {
        delegate.setTemplateDirectory(templateDirectory);
    }

    public String processTemplate(String templateName, Map<String, Object> data) {
        return delegate.processTemplate(templateName, data);
    }

}
