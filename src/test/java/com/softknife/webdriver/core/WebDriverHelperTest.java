package com.softknife.webdriver.core;

import org.testng.annotations.Test;

public class WebDriverHelperTest {

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Unsupported driver type: 'edge'.*",
            description = "An unsupported browser is refused before any driver starts, not replaced with Chrome")
    public void unsupportedBrowserIsRefused() {
        WebDriverHelper.createWebDriver("edge", true);
    }
}
