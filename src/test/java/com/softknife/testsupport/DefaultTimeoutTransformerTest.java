package com.softknife.testsupport;

import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DefaultTimeoutTransformerTest {

    @Test(description = "A test that declares no timeout runs with the default one — proves the listener is registered")
    public void defaultTimeoutIsApplied() {
        long applied = Reporter.getCurrentTestResult().getMethod().getTimeOut();

        assertEquals(applied, DefaultTimeoutTransformer.DEFAULT_TIMEOUT_MS,
                "DefaultTimeoutTransformer is not registered for this Test task");
    }

    @Test(timeOut = 30_000, description = "A test with its own timeout keeps it")
    public void explicitTimeoutIsKept() {
        assertEquals(Reporter.getCurrentTestResult().getMethod().getTimeOut(), 30_000L);
    }
}
