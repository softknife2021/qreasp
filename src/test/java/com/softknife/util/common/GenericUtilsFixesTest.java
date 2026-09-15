package com.softknife.util.common;

import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

/**
 * Behaviour of GenericUtils that was wrong before the review fixes.
 */
public class GenericUtilsFixesTest {

    @Test(description = "The URL-encoded %7Bname%7D form is substituted, not just {name}")
    public void encodedPlaceholdersAreSubstituted() {
        String result = GenericUtils.substituteVariables("http://h/%7Bid%7D/items/{kind}", Map.of("id", "42", "kind", "book"));

        assertEquals(result, "http://h/42/items/book");
    }

    @Test(description = "A placeholder with no value is left as written")
    public void unknownPlaceholderIsKept() {
        assertEquals(GenericUtils.substituteVariables("/a/{missing}", Map.of("id", "1")), "/a/{missing}");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            description = "One invalid JSON input is enough to refuse a patch")
    public void oneInvalidJsonIsRefused() {
        GenericUtils.generateJsonPatch("{\"a\":1}", "not json", null);
    }

    @Test(description = "A single key=value pair is valid input for splitToMap")
    public void singlePairSplits() {
        assertEquals(GenericUtils.splitToMap(";", "=", "name=sample"), Map.of("name", "sample"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*segment 'orphan'.*",
            description = "A segment without a separator is refused by name")
    public void segmentWithoutSeparatorIsRefused() {
        GenericUtils.splitToMap(";", "=", "a=1;orphan");
    }

    @Test(description = "isJSONValid answers false for null instead of throwing")
    public void nullIsNotValidJson() {
        assertFalse(GenericUtils.isJSONValid(null));
    }

    @Test(description = "regexMatch returns the group, or null on no match")
    public void regexMatch() {
        assertEquals(GenericUtils.regexMatch("id=42;", "id=(\\d+)", 1), "42");
        assertNull(GenericUtils.regexMatch("nothing here", "id=(\\d+)", 1));
    }
}
