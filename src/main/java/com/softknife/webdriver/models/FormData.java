package com.softknife.webdriver.models;

/**
 * @author amatsaylo on 9/26/25
 * @project qreasp
 */
import com.softknife.webdriver.enums.LocatorType;
import com.softknife.webdriver.utils.LocatorUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import java.util.Map;

/**
 * Represents data for filling a single form field.
 * Contains locator information, value to enter, and validation options.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Jacksonized
public class FormData {
    private Map<String, String> locator;
    private String value;
    private String fieldName;
    @Builder.Default
    private boolean clearBeforeType = true;
    private boolean validateAfterInput;
    private String expectedValue;

    /**
     * Creates FormData with basic locator and value.
     */
    public FormData(LocatorType locatorType, String locatorValue, String value) {
        this.locator = LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue);
        this.value = value;
        this.clearBeforeType = true;
    }

    /**
     * Creates FormData with locator, value, and field name.
     */
    public FormData(LocatorType locatorType, String locatorValue, String value, String fieldName) {
        this.locator = LocatorUtils.createLocatorMap(locatorType.name().toLowerCase(), locatorValue);
        this.value = value;
        this.fieldName = fieldName;
        this.clearBeforeType = true;
    }

    /**
     * Gets the locator type from the locator map.
     */
    public LocatorType getLocatorType() {
        // Through LocatorUtils so every strategy alias resolves — valueOf("CSS") threw for a map
        // written with the short "css" spelling LocatorUtils itself accepts.
        return LocatorUtils.toLocatorType(LocatorUtils.getStrategy(locator));
    }

    /**
     * Gets the locator value from the locator map.
     */
    public String getLocatorValue() {
        return locator != null ? locator.get("value") : null;
    }
}