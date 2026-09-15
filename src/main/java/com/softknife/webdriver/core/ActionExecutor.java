package com.softknife.webdriver.core;

import com.softknife.webdriver.enums.ActionType;
import com.softknife.webdriver.enums.RetryStrategy;
import com.softknife.webdriver.exceptions.StateExecutionException;
import com.softknife.webdriver.models.ExecutionResult;
import com.softknife.webdriver.models.FormData;
import com.softknife.webdriver.models.WebDriverState;
import com.softknife.webdriver.utils.LocatorUtils;
import com.softknife.webdriver.utils.ScreenshotUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core class responsible for executing WebDriver actions based on WebDriverState.
 *
 * <p>Three things every action gets, whatever it is:
 * <ul>
 *   <li><b>The state's timeout.</b> Element lookups wait up to {@code timeoutInSeconds}, polling every
 *       {@code pollingIntervalMs}, rather than relying on whatever implicit wait the driver was created with.</li>
 *   <li><b>The state's retry strategy.</b> A failed attempt is retried {@code maxAttempts} times with the
 *       strategy's delay. A step that is missing a required input is not retried — the answer cannot change.</li>
 *   <li><b>A result, never an exception.</b> Failures come back as an unsuccessful {@link ExecutionResult}
 *       carrying the message and, when asked for, a screenshot.</li>
 * </ul>
 *
 * @author amatsaylo on 9/26/25
 * @project qreasp
 */
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    /** Distance for SCROLL_UP / SCROLL_DOWN when the state gives no {@code offsetY}. */
    static final int DEFAULT_SCROLL_PIXELS = 500;

    /** Actions that cannot run without an element locator. */
    static final Set<ActionType> LOCATOR_REQUIRED = Collections.unmodifiableSet(EnumSet.of(
            ActionType.CLICK, ActionType.SEND_KEYS, ActionType.CLEAR, ActionType.SUBMIT,
            ActionType.GET_TEXT, ActionType.GET_ATTRIBUTE, ActionType.GET_VALUE,
            ActionType.SELECT_FROM_DROPDOWN, ActionType.SELECT_BY_INDEX, ActionType.SELECT_BY_VALUE,
            ActionType.WAIT_FOR_ELEMENT, ActionType.WAIT_FOR_ELEMENT_CLICKABLE, ActionType.WAIT_FOR_ELEMENT_VISIBLE,
            ActionType.WAIT_FOR_ELEMENT_INVISIBLE, ActionType.WAIT_FOR_TEXT_PRESENT,
            ActionType.DOUBLE_CLICK, ActionType.RIGHT_CLICK, ActionType.HOVER, ActionType.DRAG_AND_DROP,
            ActionType.CLICK_AND_HOLD, ActionType.SCROLL_TO_ELEMENT, ActionType.HIGHLIGHT_ELEMENT,
            ActionType.UPLOAD_FILE, ActionType.VALIDATE_TEXT, ActionType.VALIDATE_ATTRIBUTE,
            ActionType.VALIDATE_ELEMENT_PRESENT, ActionType.VALIDATE_ELEMENT_VISIBLE));

    /**
     * Thrown when a step lacks something it needs — a URL, an expected value, a file. Retrying cannot
     * supply it, so these fail on the first attempt regardless of the retry strategy.
     */
    static final class MissingInputException extends StateExecutionException {
        MissingInputException(String message, WebDriverState state) {
            super(message, state);
        }
    }

    /**
     * Executes the action specified in the WebDriverState.
     */
    public ExecutionResult executeAction(WebDriverState state) {
        LocalDateTime startTime = LocalDateTime.now();
        String actionTypeStr = state != null && state.getActionType() != null ? state.getActionType().name() : "UNKNOWN";
        String locatorString = state != null ? state.getLocatorString() : null;

        log.info("Executing action: {} with locator: {}", actionTypeStr, locatorString);

        if (state == null) {
            StateExecutionException e = new StateExecutionException("WebDriverState cannot be null", null);
            log.error("Refusing action {}: {}", actionTypeStr, e.getMessage());
            return failureResult(null, actionTypeStr, null, startTime, e, 0, false);
        }
        try {
            validateState(state);
        } catch (StateExecutionException e) {
            log.error("Refusing action {}: {}", actionTypeStr, e.getMessage());
            return failureResult(state, actionTypeStr, locatorString, startTime, e, 0, false);
        }

        RetryStrategy strategy = state.getRetryStrategy() != null ? state.getRetryStrategy() : RetryStrategy.NO_RETRY;
        int maxAttempts = Math.max(1, strategy.getMaxAttempts());
        Exception lastFailure = null;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                byte[] screenshotBefore = state.isTakeScreenshotBefore()
                        ? ScreenshotUtils.takeScreenshot(state.getWebDriver()) : null;

                String resultValue = performAction(state);

                byte[] screenshotAfter = state.isTakeScreenshotAfter() || state.getActionType() == ActionType.TAKE_SCREENSHOT
                        ? ScreenshotUtils.takeScreenshot(state.getWebDriver()) : null;

                LocalDateTime endTime = LocalDateTime.now();
                if (attempt > 1) {
                    log.info("Action {} succeeded on attempt {} of {}", actionTypeStr, attempt, maxAttempts);
                } else {
                    log.info("Successfully executed action: {}", actionTypeStr);
                }
                return ExecutionResult.builder()
                        .successful(true)
                        .actionType(actionTypeStr)
                        .startTime(startTime)
                        .endTime(endTime)
                        .executionDuration(Duration.between(startTime, endTime))
                        .resultValue(resultValue)
                        .screenshotBefore(screenshotBefore)
                        .screenshotAfter(screenshotAfter)
                        .elementLocator(locatorString)
                        .build();

            } catch (Exception e) {
                lastFailure = e;
                if (e instanceof MissingInputException || attempt >= maxAttempts) {
                    break;
                }
                long delay = delayBeforeNextAttempt(strategy, attempt);
                log.warn("Action {} failed on attempt {} of {} ({}); retrying in {} ms",
                        actionTypeStr, attempt, maxAttempts, e.getMessage(), delay);
                if (!sleep(delay)) {
                    break;
                }
            }
        }

        log.error("Failed to execute action: {} after {} attempt(s)", actionTypeStr, attempt, lastFailure);
        return failureResult(state, actionTypeStr, locatorString, startTime, lastFailure, attempt, true);
    }

    /**
     * How long to wait after the given (1-based) failed attempt before the next one.
     *
     * <ul>
     *   <li>{@code NO_RETRY}, {@code IMMEDIATE_RETRY}: the base delay (0).</li>
     *   <li>{@code LINEAR_BACKOFF}: base × attempt — 1 s, 2 s, 3 s …</li>
     *   <li>{@code EXPONENTIAL_BACKOFF}: base × 2^(attempt-1) — 0.5 s, 1 s, 2 s, 4 s …</li>
     * </ul>
     */
    static long delayBeforeNextAttempt(RetryStrategy strategy, int failedAttempt) {
        long base = strategy.getBaseDelayMs();
        switch (strategy) {
            case LINEAR_BACKOFF:
                return base * failedAttempt;
            case EXPONENTIAL_BACKOFF:
                return base * (1L << Math.min(failedAttempt - 1, 20));
            case IMMEDIATE_RETRY:
            case NO_RETRY:
            default:
                return base;
        }
    }

    private ExecutionResult failureResult(WebDriverState state, String actionTypeStr, String locatorString,
                                          LocalDateTime startTime, Exception failure, int attempts, boolean driverUsable) {
        byte[] screenshotOnFailure = null;
        if (driverUsable && state != null && state.isTakeScreenshotOnFailure()) {
            screenshotOnFailure = ScreenshotUtils.takeScreenshot(state.getWebDriver());
        }
        String message = failure != null ? failure.getMessage() : "Action failed";
        if (attempts > 1) {
            message = message + " (after " + attempts + " attempts)";
        }
        LocalDateTime endTime = LocalDateTime.now();
        return ExecutionResult.builder()
                .successful(false)
                .actionType(actionTypeStr)
                .startTime(startTime)
                .endTime(endTime)
                .executionDuration(Duration.between(startTime, endTime))
                .errorMessage(message)
                .stackTrace(failure != null ? stackTraceOf(failure) : null)
                .screenshotAfter(screenshotOnFailure)
                .elementLocator(locatorString)
                .build();
    }

    private static String stackTraceOf(Exception exception) {
        java.io.StringWriter sw = new java.io.StringWriter();
        exception.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Validates that the state has all required information for execution.
     */
    private void validateState(WebDriverState state) {
        if (state == null) {
            throw new StateExecutionException("WebDriverState cannot be null", null);
        }

        if (state.getWebDriver() == null) {
            throw new StateExecutionException("WebDriver cannot be null", state);
        }

        if (state.getActionType() == null) {
            throw new StateExecutionException("ActionType cannot be null", state);
        }

        if (requiresLocator(state.getActionType()) && !LocatorUtils.isValidLocatorMap(state.getLocator())) {
            throw new StateExecutionException("Valid locator is required for action: " + state.getActionType(), state);
        }
    }

    /**
     * Determines if an action type requires a locator.
     */
    static boolean requiresLocator(ActionType actionType) {
        return LOCATOR_REQUIRED.contains(actionType);
    }

    /**
     * Performs the actual WebDriver action and returns result value if applicable.
     */
    private String performAction(WebDriverState state) {
        switch (state.getActionType()) {
            // Element actions
            case CLICK:
                return performClick(state);
            case SEND_KEYS:
                return performSendKeys(state);
            case CLEAR:
                return performClear(state);
            case SUBMIT:
                return performSubmit(state);
            case GET_TEXT:
                return findElement(state).getText();
            case GET_ATTRIBUTE:
                return performGetAttribute(state);
            case GET_VALUE:
                return findElement(state).getAttribute("value");

            // Selection
            case SELECT_FROM_DROPDOWN:
                return performSelectFromDropdown(state);
            case SELECT_BY_INDEX:
                return performSelectByIndex(state);
            case SELECT_BY_VALUE:
                return performSelectByValue(state);

            // Waits
            case WAIT_FOR_ELEMENT:
                waitFor(state).until(ExpectedConditions.presenceOfElementLocated(locatorOf(state)));
                return "element found";
            case WAIT_FOR_ELEMENT_CLICKABLE:
                waitFor(state).until(ExpectedConditions.elementToBeClickable(locatorOf(state)));
                return "element clickable";
            case WAIT_FOR_ELEMENT_VISIBLE:
                waitFor(state).until(ExpectedConditions.visibilityOfElementLocated(locatorOf(state)));
                return "element visible";
            case WAIT_FOR_ELEMENT_INVISIBLE:
                waitFor(state).until(ExpectedConditions.invisibilityOfElementLocated(locatorOf(state)));
                return "element invisible";
            case WAIT_FOR_TEXT_PRESENT:
                return performWaitForTextPresent(state);

            // Navigation
            case NAVIGATE_TO:
                return performNavigateTo(state);
            case NAVIGATE_BACK:
                state.getWebDriver().navigate().back();
                return "navigated back";
            case NAVIGATE_FORWARD:
                state.getWebDriver().navigate().forward();
                return "navigated forward";
            case REFRESH:
                state.getWebDriver().navigate().refresh();
                return "page refreshed";

            // Windows and frames
            case SWITCH_TO_FRAME:
                return performSwitchToFrame(state);
            case SWITCH_TO_DEFAULT_CONTENT:
                state.getWebDriver().switchTo().defaultContent();
                return "switched to default content";
            case SWITCH_TO_WINDOW:
                return performSwitchToWindow(state);
            case CLOSE_WINDOW:
                state.getWebDriver().close();
                return "window closed";
            case MAXIMIZE_WINDOW:
                state.getWebDriver().manage().window().maximize();
                return "window maximized";
            case MINIMIZE_WINDOW:
                state.getWebDriver().manage().window().minimize();
                return "window minimized";
            case SET_WINDOW_SIZE:
                return performSetWindowSize(state);

            // Mouse
            case DOUBLE_CLICK:
                new Actions(state.getWebDriver()).doubleClick(findElement(state)).perform();
                return "double clicked";
            case RIGHT_CLICK:
                new Actions(state.getWebDriver()).contextClick(findElement(state)).perform();
                return "right clicked";
            case HOVER:
                new Actions(state.getWebDriver()).moveToElement(findElement(state)).perform();
                return "hovered";
            case DRAG_AND_DROP:
                return performDragAndDrop(state);
            case CLICK_AND_HOLD:
                new Actions(state.getWebDriver()).clickAndHold(findElement(state)).perform();
                return "clicked and holding";
            case RELEASE:
                return performRelease(state);

            // Scrolling
            case SCROLL_TO_ELEMENT:
                javascript(state).executeScript("arguments[0].scrollIntoView(true);", findElement(state));
                return "scrolled to element";
            case SCROLL_UP:
                javascript(state).executeScript("window.scrollBy(0, arguments[0]);", -scrollDistance(state));
                return "scrolled up";
            case SCROLL_DOWN:
                javascript(state).executeScript("window.scrollBy(0, arguments[0]);", scrollDistance(state));
                return "scrolled down";
            case SCROLL_TO_TOP:
                javascript(state).executeScript("window.scrollTo(0, 0);");
                return "scrolled to top";
            case SCROLL_TO_BOTTOM:
                javascript(state).executeScript("window.scrollTo(0, document.documentElement.scrollHeight);");
                return "scrolled to bottom";

            // Screenshots and debugging
            case TAKE_SCREENSHOT:
                return performTakeScreenshot(state);
            case HIGHLIGHT_ELEMENT:
                javascript(state).executeScript("arguments[0].style.outline='3px solid red';", findElement(state));
                return "element highlighted";

            case EXECUTE_JAVASCRIPT:
                return performExecuteJavascript(state);
            case UPLOAD_FILE:
                return performUploadFile(state);
            case FILL_FORM:
                return performFillForm(state);

            // Validation
            case VALIDATE_TITLE:
                return performValidateTitle(state);
            case VALIDATE_TEXT:
                return performValidateText(state);
            case VALIDATE_ATTRIBUTE:
                return performValidateAttribute(state);
            case VALIDATE_ELEMENT_PRESENT:
                waitFor(state).until(ExpectedConditions.presenceOfElementLocated(locatorOf(state)));
                return "element present";
            case VALIDATE_ELEMENT_VISIBLE:
                waitFor(state).until(ExpectedConditions.visibilityOfElementLocated(locatorOf(state)));
                return "element visible";

            default:
                // Unreachable while every constant has a case above; ActionTypeCoverageTest enforces that.
                throw new StateExecutionException("Action type not implemented: " + state.getActionType(), state);
        }
    }

    // ===== Action Implementations =====

    private String performClick(WebDriverState state) {
        findElement(state).click();
        return "clicked";
    }

    private String performSendKeys(WebDriverState state) {
        String value = require(state.getValue(), "a value to type", state);
        findElement(state).sendKeys(value);
        return "keys sent";
    }

    private String performClear(WebDriverState state) {
        findElement(state).clear();
        return "cleared";
    }

    private String performSubmit(WebDriverState state) {
        findElement(state).submit();
        return "submitted";
    }

    private String performGetAttribute(WebDriverState state) {
        String attributeName = require(state.getAttributeName(), "attributeName", state);
        return findElement(state).getAttribute(attributeName);
    }

    private String performSelectFromDropdown(WebDriverState state) {
        if (state.getDropdownIndex() != null) {
            return performSelectByIndex(state);
        }
        if (state.getDropdownValue() != null) {
            return performSelectByValue(state);
        }
        String text = state.getDropdownText() != null ? state.getDropdownText() : state.getValue();
        if (text == null) {
            throw new MissingInputException("No selection criteria provided for dropdown", state);
        }
        new Select(findElement(state)).selectByVisibleText(text);
        return "selected by text: " + text;
    }

    private String performSelectByIndex(WebDriverState state) {
        if (state.getDropdownIndex() == null) {
            throw new MissingInputException("dropdownIndex is required for " + state.getActionType(), state);
        }
        new Select(findElement(state)).selectByIndex(state.getDropdownIndex());
        return "selected by index: " + state.getDropdownIndex();
    }

    private String performSelectByValue(WebDriverState state) {
        String value = require(state.getDropdownValue() != null ? state.getDropdownValue() : state.getValue(),
                "dropdownValue", state);
        new Select(findElement(state)).selectByValue(value);
        return "selected by value: " + value;
    }

    private String performWaitForTextPresent(WebDriverState state) {
        String text = require(state.getExpectedText() != null ? state.getExpectedText() : state.getValue(),
                "expectedText", state);
        waitFor(state).until(ExpectedConditions.textToBePresentInElementLocated(locatorOf(state), text));
        return "text present: " + text;
    }

    private String performNavigateTo(WebDriverState state) {
        String url = require(state.getUrl(), "url", state);
        state.getWebDriver().navigate().to(url);
        return "navigated to: " + url;
    }

    private String performSwitchToFrame(WebDriverState state) {
        WebDriver driver = state.getWebDriver();
        if (state.getFrameIndex() >= 0) {
            driver.switchTo().frame(state.getFrameIndex());
            return "switched to frame index " + state.getFrameIndex();
        }
        if (state.getFrameNameOrId() != null && !state.getFrameNameOrId().isBlank()) {
            driver.switchTo().frame(state.getFrameNameOrId());
            return "switched to frame " + state.getFrameNameOrId();
        }
        if (LocatorUtils.isValidLocatorMap(state.getLocator())) {
            driver.switchTo().frame(findElement(state));
            return "switched to frame " + state.getLocatorString();
        }
        throw new MissingInputException("SWITCH_TO_FRAME needs frameIndex, frameNameOrId or a locator", state);
    }

    private String performSwitchToWindow(WebDriverState state) {
        String handle = require(state.getWindowHandle(), "windowHandle", state);
        state.getWebDriver().switchTo().window(handle);
        return "switched to window " + handle;
    }

    private String performSetWindowSize(WebDriverState state) {
        if (state.getWindowWidth() == null || state.getWindowHeight() == null) {
            throw new MissingInputException("SET_WINDOW_SIZE needs windowWidth and windowHeight", state);
        }
        state.getWebDriver().manage().window().setSize(new Dimension(state.getWindowWidth(), state.getWindowHeight()));
        return "window size set to " + state.getWindowWidth() + "x" + state.getWindowHeight();
    }

    private String performDragAndDrop(WebDriverState state) {
        WebElement source = findElement(state);
        Actions actions = new Actions(state.getWebDriver());
        if (LocatorUtils.isValidLocatorMap(state.getTargetLocator())) {
            actions.dragAndDrop(source, findElement(state, state.getTargetLocator())).perform();
            return "dragged to " + LocatorUtils.locatorMapToString(state.getTargetLocator());
        }
        if (state.getOffsetX() != null && state.getOffsetY() != null) {
            actions.dragAndDropBy(source, state.getOffsetX(), state.getOffsetY()).perform();
            return "dragged by " + state.getOffsetX() + "," + state.getOffsetY();
        }
        throw new MissingInputException("DRAG_AND_DROP needs a targetLocator, or offsetX and offsetY", state);
    }

    private String performRelease(WebDriverState state) {
        Actions actions = new Actions(state.getWebDriver());
        if (LocatorUtils.isValidLocatorMap(state.getLocator())) {
            actions.release(findElement(state)).perform();
        } else {
            actions.release().perform();
        }
        return "released";
    }

    private String performTakeScreenshot(WebDriverState state) {
        byte[] screenshot = ScreenshotUtils.takeScreenshot(state.getWebDriver());
        String filename = ScreenshotUtils.saveScreenshotToFile(screenshot, "manual_screenshot");
        return "screenshot saved: " + filename;
    }

    private String performExecuteJavascript(WebDriverState state) {
        String script = require(state.getJavascriptCode() != null ? state.getJavascriptCode() : state.getValue(),
                "javascriptCode", state);
        List<Object> arguments = state.getJavascriptArguments() != null ? state.getJavascriptArguments() : List.of();
        Object result = javascript(state).executeScript(script, arguments.toArray());
        return String.valueOf(result);
    }

    private String performUploadFile(WebDriverState state) {
        String path = require(state.getFilePath() != null ? state.getFilePath() : state.getValue(), "filePath", state);
        File file = new File(path);
        if (!file.isFile()) {
            throw new MissingInputException("File to upload does not exist: " + file.getAbsolutePath(), state);
        }
        findElement(state).sendKeys(file.getAbsolutePath());
        return "uploaded " + file.getName();
    }

    private String performFillForm(WebDriverState state) {
        if (state.getFormDataList() == null || state.getFormDataList().isEmpty()) {
            throw new MissingInputException("Form data list is required for FILL_FORM action", state);
        }

        List<String> filledFields = new ArrayList<>();
        for (FormData formData : state.getFormDataList()) {
            WebElement element = findElement(state, formData.getLocator());

            if (formData.isClearBeforeType()) {
                element.clear();
            }

            element.sendKeys(formData.getValue());
            filledFields.add(formData.getFieldName() != null ? formData.getFieldName() : "field");
        }

        return "filled " + filledFields.size() + " fields: " + String.join(", ", filledFields);
    }

    private String performValidateTitle(WebDriverState state) {
        String expectedTitle = require(state.getExpectedTitle(), "expectedTitle", state);
        String actualTitle = state.getWebDriver().getTitle();

        if (!expectedTitle.equals(actualTitle)) {
            throw new StateExecutionException(
                    String.format("Title validation failed. Expected: '%s', Actual: '%s'", expectedTitle, actualTitle),
                    state);
        }

        return "title validated: " + actualTitle;
    }

    private String performValidateText(WebDriverState state) {
        String expectedText = require(state.getExpectedText(), "expectedText", state);
        String actualText = findElement(state).getText();

        if (!expectedText.equals(actualText)) {
            throw new StateExecutionException(
                    String.format("Text validation failed. Expected: '%s', Actual: '%s'", expectedText, actualText),
                    state);
        }

        return "text validated: " + actualText;
    }

    private String performValidateAttribute(WebDriverState state) {
        String attributeName = require(state.getAttributeName(), "attributeName", state);
        String expected = require(state.getExpectedText() != null ? state.getExpectedText() : state.getValue(),
                "expectedText", state);
        String actual = findElement(state).getAttribute(attributeName);

        if (!expected.equals(actual)) {
            throw new StateExecutionException(
                    String.format("Attribute '%s' validation failed. Expected: '%s', Actual: '%s'", attributeName, expected, actual),
                    state);
        }
        return "attribute validated: " + attributeName + "=" + actual;
    }

    // ===== Helpers =====

    private static <T> T require(T value, String what, WebDriverState state) {
        if (value == null || (value instanceof String && ((String) value).isBlank())) {
            throw new MissingInputException(what + " is required for " + state.getActionType() + " action", state);
        }
        return value;
    }

    private static By locatorOf(WebDriverState state) {
        return LocatorUtils.getByLocator(state.getLocator());
    }

    /**
     * A wait honouring the state's timeout and polling interval. A timeout of 0 checks exactly once.
     */
    private static WebDriverWait waitFor(WebDriverState state) {
        WebDriverWait wait = new WebDriverWait(state.getWebDriver(),
                Duration.ofSeconds(Math.max(0, state.getTimeoutInSeconds())),
                Duration.ofMillis(Math.max(1, state.getPollingIntervalMs())));
        wait.ignoring(NoSuchElementException.class, StaleElementReferenceException.class);
        return wait;
    }

    /**
     * Finds the state's element, waiting up to the state's timeout for it to be present.
     */
    private WebElement findElement(WebDriverState state) {
        return findElement(state, state.getLocator());
    }

    private WebElement findElement(WebDriverState state, Map<String, String> locator) {
        By by = LocatorUtils.getByLocator(locator);
        if (state.getTimeoutInSeconds() <= 0) {
            // Exactly one lookup. A zero-length WebDriverWait may still poll once more when the clock
            // has not advanced, which makes "no wait" non-deterministic.
            return state.getWebDriver().findElement(by);
        }
        return waitFor(state).until(driver -> driver.findElement(by));
    }

    private static JavascriptExecutor javascript(WebDriverState state) {
        if (!(state.getWebDriver() instanceof JavascriptExecutor)) {
            throw new MissingInputException("The driver does not support JavaScript execution", state);
        }
        return (JavascriptExecutor) state.getWebDriver();
    }

    private static int scrollDistance(WebDriverState state) {
        return state.getOffsetY() != null ? Math.abs(state.getOffsetY()) : DEFAULT_SCROLL_PIXELS;
    }
}
