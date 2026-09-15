package com.softknife.util.common;

import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertTrue;

public class RBFileUtilsClasspathTest {

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*does/not/exist.txt.*",
            description = "A missing resource names itself instead of throwing NullPointerException")
    public void missingResourceIsNamed() {
        RBFileUtils.getFileOnClassPath("does/not/exist.txt");
    }

    @Test(description = "An existing resource resolves to a real file")
    public void existingResourceIsAFile() {
        File file = RBFileUtils.getFileOnClassPath("wiremock/wiremock-stubs.json");

        assertTrue(file.isFile(), file.getAbsolutePath());
    }
}
