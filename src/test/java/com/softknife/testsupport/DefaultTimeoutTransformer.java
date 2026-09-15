package com.softknife.testsupport;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Gives every test without its own timeout a default one.
 *
 * <p>A stuck chromedriver session once held the whole build for over four minutes, because no test
 * had a timeout. Registered in build.gradle for every Test task, so a new test is covered without
 * anyone remembering to add {@code timeOut}.
 */
public class DefaultTimeoutTransformer implements IAnnotationTransformer {

    /** Two minutes: longer than any healthy test here, short enough to fail a hung one quickly. */
    public static final long DEFAULT_TIMEOUT_MS = 120_000;

    @Override
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        if (annotation.getTimeOut() <= 0) {
            annotation.setTimeOut(DEFAULT_TIMEOUT_MS);
        }
    }
}
