package com.softknife.data.templating;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TemplateManagerValidationTest {

    private TemplateManager templateManager;

    @BeforeClass(alwaysRun = true)
    public void setUp() {
        templateManager = new TemplateManager("src/test/resources/payload/template/with-metadata",
                new String[]{"ftl", "json"}, true, ";", "=");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Both templateName and version are required.*",
            description = "A blank version is refused up front, not reported later as a missing template")
    public void blankVersionIsRefused() throws Exception {
        templateManager.processTemplateWithJsonInput("sample3", " ");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Both templateName and version are required.*",
            description = "A blank name is refused up front")
    public void blankNameIsRefused() throws Exception {
        templateManager.processTemplateWithJsonInput("", "0.1");
    }

    @Test(description = "A valid name and version still render — the check refuses only what it should")
    public void validNameAndVersionRender() throws Exception {
        assertNotNull(templateManager.processTemplateWithJsonInput("sample3", "0.1"));
    }
}
