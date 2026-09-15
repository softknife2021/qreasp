package com.softknife.util.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.common.base.Splitter;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.softknife.resource.GlobalResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author softknife on 10/15/18
 * @project qreasp
 */

public class GenericUtils {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Configuration jacksonConfiguration = GlobalResourceManager.getInstance().getConfiguration();

    private static final Pattern BRACE_VARIABLE = Pattern.compile("\\{(.+?)\\}");
    private static final Pattern ENCODED_BRACE_VARIABLE = Pattern.compile("%7B(.+?)%7D", Pattern.CASE_INSENSITIVE);

    /**
     * Splits {@code key=value;key=value} (with the given separators) into a map.
     *
     * <p>A single pair is valid. Every segment must contain the key/value separator and a key.
     *
     * @throws IllegalArgumentException naming the offending input when it is not in that shape
     */
    public static Map<String, String> splitToMap(String splitter, String keyValueSeparator, String keysAndValues) {
        if (keysAndValues == null || keysAndValues.isEmpty()) {
            throw new IllegalArgumentException("Invalid String for splitting: nothing to split. Example: key=value or key=value;key=value");
        }
        for (String segment : keysAndValues.split(Pattern.quote(splitter), -1)) {
            int separator = segment.indexOf(keyValueSeparator);
            if (separator <= 0 || segment.indexOf(keyValueSeparator, separator + keyValueSeparator.length()) != -1) {
                throw new IllegalArgumentException("Invalid String for splitting: segment '" + segment + "' of '"
                        + keysAndValues + "' is not key" + keyValueSeparator + "value. Example: key=value or key=value;key=value");
            }
        }
        return Splitter.on(splitter).withKeyValueSeparator(keyValueSeparator).split(keysAndValues);
    }

    /**
     * Replaces {@code {name}} and its URL-encoded form {@code %7Bname%7D} with values from the map.
     * Placeholders with no entry in the map are left as they are.
     */
    public static String substituteVariables(String template, Map<String, String> variables) {
        if (template == null) {
            throw new NullPointerException("String template SHOULD NOT BE NULL");
        }
        if (template.isEmpty()) {
            throw new IllegalArgumentException("String template SHOULD NOT BE EMPTY");
        }
        if (variables == null) {
            throw new NullPointerException("Map variables SHOULD NOT BE NULL");
        }
        // Both forms, always. The encoded form used to be reachable only when the plain pattern "had
        // no groups", which a compiled pattern with a group never does, so it was never substituted.
        String substituted = processMatcher(BRACE_VARIABLE.matcher(template), variables);
        return processMatcher(ENCODED_BRACE_VARIABLE.matcher(substituted), variables);
    }

    private static String processMatcher(Matcher matcher, Map<String, String> variables) {
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            if (variables.containsKey(matcher.group(1))) {
                String replacement = variables.get(matcher.group(1));
                matcher.appendReplacement(buffer, replacement != null ? Matcher.quoteReplacement(replacement) : "null");
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static Optional<String> convertYamlToJson(ObjectMapper objectMapper, ObjectMapper yamlObjectMapper, String yaml) {
        try {
            Object obj = yamlObjectMapper.readValue(yaml, Object.class);
            return Optional.of(objectMapper.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            logger.warn("YAML could not be converted to JSON: {}", e.getOriginalMessage());
        }
        return Optional.empty();
    }

    public static String removeKeyWithValueFromJson(String json, String pathSpec) {
        return JsonPath.using(jacksonConfiguration).parse(json).delete(pathSpec).jsonString();
    }

    public static String setKeyWithValueFromJson(String json, String pathSpec, Object value) {
        return JsonPath.using(jacksonConfiguration).parse(json).set(pathSpec, value).jsonString();
    }

    public static boolean isJSONValid(String jsonInString) {
        if (jsonInString == null) {
            return false;
        }
        try {
            GlobalResourceManager.getInstance().getObjectMapper().readTree(jsonInString);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The given group of the first match of {@code pattern} in {@code text}, or null when nothing matches.
     */
    public static String regexMatch(String text, String pattern, int expectedGroup) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        if (m.find()) {
            logger.debug("Expected group {} return value {} ", expectedGroup, m.group(expectedGroup));
            return m.group(expectedGroup);
        }
        logger.warn("No match for pattern {}", pattern);
        return null;
    }

    /**
     * @deprecated use {@link #regexMatch(String, String, int)}; this name breaks Java method naming and will be removed.
     */
    @Deprecated
    public static String RegexMatcher(String text, String pattern, int expectedGroup) {
        return regexMatch(text, pattern, expectedGroup);
    }

    public static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }


    /**
     * The JSON Patch that turns {@code json1} into {@code json2}.
     *
     * @throws IllegalArgumentException when either input is not valid JSON
     */
    public static JsonNode generateJsonPatch(String json1, String json2, @Nullable ObjectMapper mapper) {
        // Either one invalid is enough to refuse. This was `&&`, so one bad input reached readTree
        // and surfaced as a Jackson parse error instead.
        if (!GenericUtils.isJSONValid(json1) || !GenericUtils.isJSONValid(json2)) {
            throw new IllegalArgumentException("One of provided strings is not valid JSON object");
        }
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        JsonNode jsonNode1;
        JsonNode jsonNode2;
        try {
            jsonNode1 = mapper.readTree(json1);
            jsonNode2 = mapper.readTree(json2);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getOriginalMessage(), e);
        }
        return JsonDiff.asJson(jsonNode1, jsonNode2);
    }
}
