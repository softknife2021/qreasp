package com.softknife.webdriver.utils;

import com.softknife.webdriver.enums.LocatorType;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Utility class for handling WebDriver locators.
 * Converts locator maps to Selenium By objects.
 */
public class LocatorUtils {

    private static final Logger log = LoggerFactory.getLogger(LocatorUtils.class);

    private static final String STRATEGY_KEY = "strategy";
    private static final String VALUE_KEY = "value";

    // Locator strategy constants
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String CLASS_NAME = "className";
    public static final String TAG_NAME = "tagName";
    public static final String LINK_TEXT = "linkText";
    public static final String PARTIAL_LINK_TEXT = "partialLinkText";
    public static final String CSS_SELECTOR = "css";
    public static final String XPATH = "xpath";

    /**
     * Validates if a locator map is valid and contains required keys.
     *
     * @param locator the locator map to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidLocatorMap(Map<String, String> locator) {
        if (locator == null || locator.isEmpty()) {
            return false;
        }

        String strategy = locator.get(STRATEGY_KEY);
        String value = locator.get(VALUE_KEY);

        return strategy != null && !strategy.trim().isEmpty()
                && value != null && !value.trim().isEmpty();
    }

    /**
     * Converts a locator map to a Selenium By object.
     *
     * @param locator map containing strategy and value
     * @return Selenium By locator
     * @throws IllegalArgumentException if locator is invalid or strategy is not supported
     */
    public static By getByLocator(Map<String, String> locator) {
        if (!isValidLocatorMap(locator)) {
            throw new IllegalArgumentException("Invalid locator map: " + locator);
        }

        String strategy = locator.get(STRATEGY_KEY).toLowerCase().trim();
        String value = locator.get(VALUE_KEY);

        log.debug("Creating By locator with strategy: {} and value: {}", strategy, value);

        switch (strategy) {
            case ID:
                return By.id(value);
            case NAME:
                return By.name(value);
            case CLASS_NAME:
            case "classname":
            case "class":
            case "class_name":
                return By.className(value);
            case TAG_NAME:
            case "tagname":
            case "tag":
            case "tag_name":
                return By.tagName(value);
            case LINK_TEXT:
            case "linktext":
            case "link":
            case "link_text":
                return By.linkText(value);
            case PARTIAL_LINK_TEXT:
            case "partiallinktext":
            case "partiallink":
            case "partial_link_text":
                return By.partialLinkText(value);
            case CSS_SELECTOR:
            case "cssselector":
            case "css selector":
            case "css_selector":
                return By.cssSelector(value);
            case XPATH:
                return By.xpath(value);
            default:
                throw new IllegalArgumentException("Unsupported locator strategy: " + strategy);
        }
    }

    /**
     * Creates a locator map with strategy and value.
     *
     * <p>This is the only place a locator map is built. {@link com.softknife.webdriver.models.WebDriverState}
     * and {@link com.softknife.webdriver.models.FormData} both delegate here, so every map the
     * executor reads has the same keys.
     *
     * @param strategy the locator strategy (id, xpath, css, etc.)
     * @param value the locator value
     * @return map containing strategy and value
     */
    public static Map<String, String> createLocatorMap(String strategy, String value) {
        return Map.of(STRATEGY_KEY, strategy, VALUE_KEY, value);
    }

    /**
     * Creates a locator map from a {@link LocatorType}.
     */
    public static Map<String, String> createLocatorMap(LocatorType locatorType, String value) {
        return createLocatorMap(locatorType.name().toLowerCase(), value);
    }

    /**
     * The {@link LocatorType} a strategy string names, accepting every alias
     * {@link #getByLocator(Map)} accepts ("css", "css_selector", "cssSelector", …).
     *
     * @param strategy the strategy as stored in a locator map
     * @return the matching type, or null when the strategy is null or unknown
     */
    public static LocatorType toLocatorType(String strategy) {
        if (strategy == null) {
            return null;
        }
        switch (strategy.toLowerCase().trim()) {
            case "id":
                return LocatorType.ID;
            case "name":
                return LocatorType.NAME;
            case "classname":
            case "class":
            case "class_name":
                return LocatorType.CLASS_NAME;
            case "tagname":
            case "tag":
            case "tag_name":
                return LocatorType.TAG_NAME;
            case "linktext":
            case "link":
            case "link_text":
                return LocatorType.LINK_TEXT;
            case "partiallinktext":
            case "partiallink":
            case "partial_link_text":
                return LocatorType.PARTIAL_LINK_TEXT;
            case "css":
            case "cssselector":
            case "css selector":
            case "css_selector":
                return LocatorType.CSS_SELECTOR;
            case "xpath":
                return LocatorType.XPATH;
            default:
                return null;
        }
    }

    /**
     * Converts a By locator to a readable string representation.
     *
     * @param by the By locator
     * @return string representation of the locator
     */
    public static String byToString(By by) {
        if (by == null) {
            return "null";
        }
        return by.toString();
    }

    /**
     * Extracts the locator strategy from a locator map.
     *
     * @param locator the locator map
     * @return the strategy string, or null if not found
     */
    public static String getStrategy(Map<String, String> locator) {
        if (locator == null) {
            return null;
        }
        return locator.get(STRATEGY_KEY);
    }

    /**
     * Extracts the locator value from a locator map.
     *
     * @param locator the locator map
     * @return the value string, or null if not found
     */
    public static String getValue(Map<String, String> locator) {
        if (locator == null) {
            return null;
        }
        return locator.get(VALUE_KEY);
    }

    /**
     * Converts a locator map to a readable string representation.
     *
     * @param locator the locator map
     * @return string representation like "xpath: //div[@id='test']"
     */
    public static String locatorMapToString(Map<String, String> locator) {
        if (!isValidLocatorMap(locator)) {
            return "invalid locator";
        }
        return String.format("%s: %s", getStrategy(locator), getValue(locator));
    }
}