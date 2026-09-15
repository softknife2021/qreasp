package com.softknife.webdriver.facade;

import com.softknife.webdriver.core.ActionExecutor;
import com.softknife.webdriver.enums.ActionType;
import com.softknife.webdriver.enums.LocatorType;
import com.softknife.webdriver.enums.RetryStrategy;
import com.softknife.webdriver.models.ExecutionResult;
import com.softknife.webdriver.models.FormData;
import com.softknife.webdriver.models.WebDriverState;
import com.softknife.webdriver.utils.LocatorUtils;
import org.openqa.selenium.WebDriver;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Fluent wrapper for simplified WebDriver operations.
 * Provides a clean API for common actions while maintaining the state-driven architecture underneath.
 *
 * Usage:
 * <pre>
 * BrowserContext browser = BrowserContext.with(driver);
 * browser.navigateTo("https://example.com");
 * browser.click(LocatorType.ID, "submit-btn");
 * browser.sendKeys(LocatorType.NAME, "username", "testuser");
 * </pre>
 */
public class BrowserContext {

    private final WebDriver driver;
    private final ActionExecutor executor;
    private boolean defaultScreenshotOnFailure = true;
    private int defaultTimeout = 10;
    private RetryStrategy defaultRetryStrategy = RetryStrategy.NO_RETRY;

    private BrowserContext(WebDriver driver) {
        this.driver = driver;
        this.executor = new ActionExecutor();
    }

    /**
     * Creates a new BrowserContext with the given WebDriver.
     */
    public static BrowserContext with(WebDriver driver) {
        return new BrowserContext(driver);
    }

    // Configuration methods

    public BrowserContext withScreenshotOnFailure(boolean enabled) {
        this.defaultScreenshotOnFailure = enabled;
        return this;
    }

    public BrowserContext withDefaultTimeout(int seconds) {
        this.defaultTimeout = seconds;
        return this;
    }

    public BrowserContext withDefaultRetry(RetryStrategy strategy) {
        this.defaultRetryStrategy = strategy;
        return this;
    }

    // Navigation actions

    public ExecutionResult navigateTo(String url) {
        return navigateTo(url, false);
    }

    public ExecutionResult navigateTo(String url, boolean takeScreenshot) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.NAVIGATE_TO)
                .url(url)
                .takeScreenshotAfter(takeScreenshot)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult navigateBack() {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.NAVIGATE_BACK)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult navigateForward() {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.NAVIGATE_FORWARD)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult refresh() {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.REFRESH)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    // Element interaction actions

    public ExecutionResult click(LocatorType locatorType, String locatorValue) {
        return click(locatorType, locatorValue, false);
    }

    public ExecutionResult click(LocatorType locatorType, String locatorValue, boolean takeScreenshot) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.CLICK)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .retryStrategy(defaultRetryStrategy)
                .takeScreenshotAfter(takeScreenshot)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult doubleClick(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.DOUBLE_CLICK)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult rightClick(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.RIGHT_CLICK)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult sendKeys(LocatorType locatorType, String locatorValue, String text) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.SEND_KEYS)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .value(text)
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult clear(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.CLEAR)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult submit(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.SUBMIT)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    // Data retrieval actions

    public String getText(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.GET_TEXT)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        ExecutionResult result = executor.executeAction(state);
        return result.getResultValue();
    }

    public String getAttribute(LocatorType locatorType, String locatorValue, String attributeName) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.GET_ATTRIBUTE)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .attributeName(attributeName)
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        ExecutionResult result = executor.executeAction(state);
        return result.getResultValue();
    }

    public String getValue(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.GET_VALUE)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        ExecutionResult result = executor.executeAction(state);
        return result.getResultValue();
    }

    // Selection actions

    public ExecutionResult selectByText(LocatorType locatorType, String locatorValue, String visibleText) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.SELECT_FROM_DROPDOWN)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .dropdownText(visibleText)
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult selectByValue(LocatorType locatorType, String locatorValue, String value) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.SELECT_FROM_DROPDOWN)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .dropdownValue(value)
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult selectByIndex(LocatorType locatorType, String locatorValue, int index) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.SELECT_FROM_DROPDOWN)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .dropdownIndex(index)
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    // Wait actions

    public ExecutionResult waitForElement(LocatorType locatorType, String locatorValue) {
        return waitForElement(locatorType, locatorValue, defaultTimeout);
    }

    public ExecutionResult waitForElement(LocatorType locatorType, String locatorValue, int timeoutSeconds) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.WAIT_FOR_ELEMENT)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(timeoutSeconds)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult waitForClickable(LocatorType locatorType, String locatorValue) {
        return waitForClickable(locatorType, locatorValue, defaultTimeout);
    }

    public ExecutionResult waitForClickable(LocatorType locatorType, String locatorValue, int timeoutSeconds) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.WAIT_FOR_ELEMENT_CLICKABLE)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(timeoutSeconds)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult waitForVisible(LocatorType locatorType, String locatorValue) {
        return waitForVisible(locatorType, locatorValue, defaultTimeout);
    }

    public ExecutionResult waitForVisible(LocatorType locatorType, String locatorValue, int timeoutSeconds) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.WAIT_FOR_ELEMENT_VISIBLE)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(timeoutSeconds)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    // Mouse actions

    public ExecutionResult hover(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.HOVER)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult scrollToElement(LocatorType locatorType, String locatorValue) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.SCROLL_TO_ELEMENT)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    // Form actions

    public ExecutionResult fillForm(FormData... formFields) {
        return fillForm(Arrays.asList(formFields));
    }

    public ExecutionResult fillForm(List<FormData> formFields) {
        WebDriverState.WebDriverStateBuilder stateBuilder = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.FILL_FORM)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure);

        for (FormData formData : formFields) {
            stateBuilder.formData(formData);
        }

        return executor.executeAction(stateBuilder.build());
    }

    // Validation actions

    public ExecutionResult validateTitle(String expectedTitle) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.VALIDATE_TITLE)
                .expectedTitle(expectedTitle)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult validateText(LocatorType locatorType, String locatorValue, String expectedText) {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.VALIDATE_TEXT)
                .locator(LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue))
                .expectedText(expectedText)
                .timeoutInSeconds(defaultTimeout)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure)
                .build();
        return executor.executeAction(state);
    }

    // Window management

    public ExecutionResult maximizeWindow() {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.MAXIMIZE_WINDOW)
                .build();
        return executor.executeAction(state);
    }

    public ExecutionResult takeScreenshot() {
        WebDriverState state = WebDriverState.builder()
                .webDriver(driver)
                .actionType(ActionType.TAKE_SCREENSHOT)
                .build();
        return executor.executeAction(state);
    }

    // Waits and validations added with the full executor

    public ExecutionResult waitForInvisible(LocatorType locatorType, String locatorValue) {
        return execute(elementState(ActionType.WAIT_FOR_ELEMENT_INVISIBLE, locatorType, locatorValue));
    }

    public ExecutionResult waitForText(LocatorType locatorType, String locatorValue, String text) {
        return execute(elementState(ActionType.WAIT_FOR_TEXT_PRESENT, locatorType, locatorValue).expectedText(text));
    }

    public ExecutionResult validateAttribute(LocatorType locatorType, String locatorValue, String attributeName, String expectedValue) {
        return execute(elementState(ActionType.VALIDATE_ATTRIBUTE, locatorType, locatorValue)
                .attributeName(attributeName).expectedText(expectedValue));
    }

    public ExecutionResult validateElementPresent(LocatorType locatorType, String locatorValue) {
        return execute(elementState(ActionType.VALIDATE_ELEMENT_PRESENT, locatorType, locatorValue));
    }

    public ExecutionResult validateElementVisible(LocatorType locatorType, String locatorValue) {
        return execute(elementState(ActionType.VALIDATE_ELEMENT_VISIBLE, locatorType, locatorValue));
    }

    // Frames and windows

    public ExecutionResult switchToFrame(LocatorType locatorType, String locatorValue) {
        return execute(elementState(ActionType.SWITCH_TO_FRAME, locatorType, locatorValue));
    }

    public ExecutionResult switchToFrame(int index) {
        return execute(pageState(ActionType.SWITCH_TO_FRAME).frameIndex(index));
    }

    public ExecutionResult switchToDefaultContent() {
        return execute(pageState(ActionType.SWITCH_TO_DEFAULT_CONTENT));
    }

    public ExecutionResult switchToWindow(String windowHandle) {
        return execute(pageState(ActionType.SWITCH_TO_WINDOW).windowHandle(windowHandle));
    }

    public ExecutionResult closeWindow() {
        return execute(pageState(ActionType.CLOSE_WINDOW));
    }

    public ExecutionResult setWindowSize(int width, int height) {
        return execute(pageState(ActionType.SET_WINDOW_SIZE).windowWidth(width).windowHeight(height));
    }

    // Mouse, scrolling, scripts, files

    public ExecutionResult dragAndDrop(LocatorType sourceType, String sourceValue, LocatorType targetType, String targetValue) {
        return execute(elementState(ActionType.DRAG_AND_DROP, sourceType, sourceValue)
                .targetLocator(LocatorUtils.createLocatorMap(targetType, targetValue)));
    }

    public ExecutionResult scrollDown(int pixels) {
        return execute(pageState(ActionType.SCROLL_DOWN).offsetY(pixels));
    }

    public ExecutionResult scrollUp(int pixels) {
        return execute(pageState(ActionType.SCROLL_UP).offsetY(pixels));
    }

    public ExecutionResult scrollToTop() {
        return execute(pageState(ActionType.SCROLL_TO_TOP));
    }

    public ExecutionResult scrollToBottom() {
        return execute(pageState(ActionType.SCROLL_TO_BOTTOM));
    }

    public ExecutionResult executeJavaScript(String script, Object... arguments) {
        WebDriverState.WebDriverStateBuilder builder = pageState(ActionType.EXECUTE_JAVASCRIPT).javascriptCode(script);
        for (Object argument : arguments) {
            builder.javascriptArgument(argument);
        }
        return execute(builder);
    }

    public ExecutionResult uploadFile(LocatorType locatorType, String locatorValue, String filePath) {
        return execute(elementState(ActionType.UPLOAD_FILE, locatorType, locatorValue).filePath(filePath));
    }

    private WebDriverState.WebDriverStateBuilder pageState(ActionType actionType) {
        return WebDriverState.builder()
                .webDriver(driver)
                .actionType(actionType)
                .timeoutInSeconds(defaultTimeout)
                .retryStrategy(defaultRetryStrategy)
                .takeScreenshotOnFailure(defaultScreenshotOnFailure);
    }

    private WebDriverState.WebDriverStateBuilder elementState(ActionType actionType, LocatorType locatorType, String locatorValue) {
        return pageState(actionType).locator(LocatorUtils.createLocatorMap(locatorType, locatorValue));
    }

    private ExecutionResult execute(WebDriverState.WebDriverStateBuilder builder) {
        return executor.executeAction(builder.build());
    }

    // Get underlying driver if needed for complex operations
    public WebDriver getDriver() {
        return driver;
    }

    // Quit driver
    public void quit() {
        if (driver != null) {
            driver.quit();
        }
    }
}