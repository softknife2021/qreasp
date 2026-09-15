/*
 * *
 *  * Created by SOFTKNIFE on 6/15/21
 *  * @author Ed Vayn
 *  * @project qreasp
 *  * Copyright (c) 2021 . All rights reserved.
 *  * Last modified 6/15/21
 *
 */

package com.softknife.data.templating;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softknife.resource.GlobalResourceManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonTemplateMapper {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JsonTemplateMapper.class);
    private static final ObjectMapper OBJECT_MAPPER = GlobalResourceManager.getInstance().getObjectMapper();

    public static Map<String, Object> convertJsonToMap(String json) throws IOException {
        return OBJECT_MAPPER.readValue(json, new TypeReference<HashMap<String, Object>>(){});
    }
    public static  List<Map<String, Object>> splitMapEntries(Map<String, Object> inputMap, String matchKey, String keyToSplit,
                                                      String delim, String keyDelim) throws Exception {
        List<Map<String, Object>> entries = (List)inputMap.get(matchKey);
        entries.stream().forEach(e-> {
            Map<String, String> split = splitByKeysValues(String.valueOf(e.get(keyToSplit)), delim, keyDelim);
            e.put(keyToSplit, split);
        });
        return entries;
    }

    public static  List<Map<String, Object>> splitTestParametersEntries(Map<String, Object> inputMap, String matchKey, String keyToSplit,
                                                             String delim, String keyDelim) throws Exception {
        List<Map<String, Object>> entries = (List)inputMap.get(matchKey);
        entries.stream().forEach(e-> {
            StringBuilder sb = new StringBuilder();
            Map<String, String> split = splitByKeysValues(String.valueOf(e.get(keyToSplit)), delim, keyDelim);
            split.entrySet().stream().forEach(entry -> sb.append("{\"name\":\"" + entry.getKey() + "\"," + "\"value\":" + "\"" + entry.getValue().trim() + "\"},"));
            e.put(keyToSplit, sb.toString().substring(0, sb.length() -1));
        });
        return entries;
    }
    public static Map<String, String> splitByKeysValues(String input, String delim, String keyDelim) {
        Map<String, String> parameters = new HashMap<>();
        List<String> split = Arrays.asList(input.split(delim));
        for (String entry : split) {
            String[] keyVal = entry.split(keyDelim);
            if (keyVal.length < 2) {
                // Named and skipped, rather than a stack trace that stopped at the first bad entry
                // and silently returned whatever had been parsed so far.
                LOGGER.warn("Skipping '{}' in '{}': it has no '{}' separator", entry, input, keyDelim);
                continue;
            }
            parameters.put(keyVal[0], keyVal[1]);
        }
        return parameters;
    }
}
