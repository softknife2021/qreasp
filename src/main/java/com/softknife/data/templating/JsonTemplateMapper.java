/*
 * *
 *  * Created by RESTBUSTERS on 6/15/21
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
        try {
            split.stream().forEach(e -> {
                String[] keyVal = e.split(keyDelim);
                parameters.put(keyVal[0], keyVal[1]);
            });
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return parameters;
    }
}
