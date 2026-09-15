package com.softknife.webdriver.core;

import com.softknife.webdriver.enums.ActionType;
import com.softknife.webdriver.enums.LocatorType;
import com.softknife.webdriver.enums.RetryStrategy;
import com.softknife.webdriver.models.ExecutionResult;
import com.softknife.webdriver.models.FormData;
import com.softknife.webdriver.models.WebDriverState;
import com.softknife.webdriver.utils.LocatorUtils;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * The executor's contract, checked without a browser.
 *
 * <p>These are the promises the library makes to its users and did not keep: every advertised
 * action runs, a state built with {@code withLocator} is accepted, the retry strategy is applied,
 * and a step missing an input is refused without pointless retries.
 */
public class ActionExecutorContractTest {

    private final ActionExecutor executor = new ActionExecutor();

    /** A state with every field an action could need, so only an unimplemented action can fail with "not implemented". */
    private WebDriverState fullyPopulatedState(ActionType actionType, FakeWebDriver fake) throws Exception {
        File upload = File.createTempFile("qreasp-upload", ".txt");
        upload.deleteOnExit();
        Files.writeString(upload.toPath(), "x");
        return WebDriverState.builder()
                .webDriver(fake.driver())
                .actionType(actionType)
                .locator(LocatorUtils.createLocatorMap(LocatorType.ID, "field"))
                .targetLocator(LocatorUtils.createLocatorMap(LocatorType.ID, "target"))
                .value("value")
                .url("http://localhost/")
                .attributeName("data-x")
                .expectedText("")
                .expectedTitle("")
                .dropdownIndex(0)
                .dropdownValue("v")
                .windowHandle("handle")
                .frameNameOrId("frame")
                .windowWidth(800)
                .windowHeight(600)
                .offsetX(10)
                .offsetY(10)
                .javascriptCode("return 1;")
                .filePath(upload.getAbsolutePath())
                .formData(new FormData(LocatorType.ID, "field", "value"))
                .timeoutInSeconds(0)
                .build();
    }

    @Test(description = "Every ActionType is implemented — a constant added tomorrow fails here the same day")
    public void everyActionTypeIsImplemented() throws Exception {
        for (ActionType actionType : ActionType.values()) {
            ExecutionResult result = executor.executeAction(fullyPopulatedState(actionType, FakeWebDriver.create()));

            assertNotNull(result, actionType + " returned no result");
            String error = String.valueOf(result.getErrorMessage());
            assertFalse(error.contains("not implemented"), actionType + " is advertised but not implemented: " + error);
        }
    }

    @Test(description = "A state built with withLocator is accepted and reaches the driver")
    public void withLocatorProducesAStateTheExecutorAccepts() {
        FakeWebDriver fake = FakeWebDriver.create();
        WebDriverState state = WebDriverState.create(fake.driver())
                .withAction(ActionType.CLICK)
                .withLocator(LocatorType.ID, "submit")
                .withTimeout(0);

        ExecutionResult result = executor.executeAction(state);

        assertTrue(result.isSuccessful(), "refused a withLocator state: " + result.getErrorMessage());
        assertEquals(result.getElementLocator(), "ID: submit", "the log and result must name the locator");
        assertTrue(fake.findElementCalls.get() >= 1, "the driver was never asked for the element");
    }

    @Test(description = "IMMEDIATE_RETRY recovers from an element that appears on the second attempt")
    public void retryStrategyIsApplied() {
        FakeWebDriver fake = FakeWebDriver.create().failFindElement(1);
        WebDriverState state = WebDriverState.builder()
                .webDriver(fake.driver())
                .actionType(ActionType.CLICK)
                .locator(LocatorUtils.createLocatorMap(LocatorType.ID, "late"))
                .timeoutInSeconds(0)
                .retryStrategy(RetryStrategy.IMMEDIATE_RETRY)
                .build();

        ExecutionResult result = executor.executeAction(state);

        assertTrue(result.isSuccessful(), "retry should have recovered: " + result.getErrorMessage());
        assertEquals(fake.findElementCalls.get(), 2);
    }

    @Test(description = "NO_RETRY fails on the first miss — the retry above is the strategy, not luck")
    public void noRetryFailsOnFirstMiss() {
        FakeWebDriver fake = FakeWebDriver.create().failFindElement(1);
        WebDriverState state = WebDriverState.builder()
                .webDriver(fake.driver())
                .actionType(ActionType.CLICK)
                .locator(LocatorUtils.createLocatorMap(LocatorType.ID, "late"))
                .timeoutInSeconds(0)
                .build();

        ExecutionResult result = executor.executeAction(state);

        assertFalse(result.isSuccessful());
        assertEquals(fake.findElementCalls.get(), 1);
    }

    @Test(description = "A missing input is refused once, never retried")
    public void missingInputIsNotRetried() {
        FakeWebDriver fake = FakeWebDriver.create();
        WebDriverState state = WebDriverState.builder()
                .webDriver(fake.driver())
                .actionType(ActionType.NAVIGATE_TO)
                .retryStrategy(RetryStrategy.EXPONENTIAL_BACKOFF)
                .build();

        long start = System.nanoTime();
        ExecutionResult result = executor.executeAction(state);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(result.isSuccessful());
        assertTrue(result.getErrorMessage().contains("url is required"), result.getErrorMessage());
        assertTrue(elapsedMs < 400, "a missing input was retried with backoff (" + elapsedMs + " ms)");
    }

    @Test(description = "A null state is a failed result, not a NullPointerException")
    public void nullStateIsAResult() {
        ExecutionResult result = executor.executeAction(null);

        assertFalse(result.isSuccessful());
        assertEquals(result.getErrorMessage(), "WebDriverState cannot be null");
    }

    @Test(description = "Backoff delays follow the strategy's documented shape")
    public void backoffDelays() {
        assertEquals(ActionExecutor.delayBeforeNextAttempt(RetryStrategy.IMMEDIATE_RETRY, 1), 0);
        assertEquals(ActionExecutor.delayBeforeNextAttempt(RetryStrategy.LINEAR_BACKOFF, 1), 1000);
        assertEquals(ActionExecutor.delayBeforeNextAttempt(RetryStrategy.LINEAR_BACKOFF, 2), 2000);
        assertEquals(ActionExecutor.delayBeforeNextAttempt(RetryStrategy.EXPONENTIAL_BACKOFF, 1), 500);
        assertEquals(ActionExecutor.delayBeforeNextAttempt(RetryStrategy.EXPONENTIAL_BACKOFF, 3), 2000);
    }
}
