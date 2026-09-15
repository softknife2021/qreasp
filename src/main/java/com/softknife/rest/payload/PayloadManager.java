package com.softknife.rest.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Criteria;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import com.softknife.resource.GlobalResourceManager;
import com.softknife.rest.payload.model.PayloadTemplate;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Sasha Matsaylo on 10/15/18
 * @project qreasp
 * @deprecated Use {@link FreeMarkerPayloadManager} instead. This class has incomplete functionality.
 */
@Deprecated
public class PayloadManager {

    private static PayloadManager instance;
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private String payloadsAsJson;
    private List<PayloadTemplate> payloadTemplates;
    private ObjectMapper objectMapper = GlobalResourceManager.getInstance().getObjectMapper();
    private Map<String, Object> defaultMap;

    private PayloadManager(String jsonPayloads) {
        initPayloads(jsonPayloads);
    }

    private void initPayloads(String jsonPayloads) {
        this.payloadsAsJson = jsonPayloads;
        try {
            this.payloadTemplates = objectMapper.readValue(this.payloadsAsJson, new TypeReference<List<PayloadTemplate>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse payload templates: {}", e.getMessage(), e);
        }
    }

    private static final Object LOCK = new Object();

    /**
     * The manager for these payload definitions. Asking with different definitions builds a new
     * manager; it used to return the first one unchanged and silently ignore the new definitions.
     */
    public static PayloadManager getInstance(String jsonPayloads) throws IOException {
        synchronized (LOCK) {
            if (instance == null || !java.util.Objects.equals(instance.payloadsAsJson, jsonPayloads)) {
                if (instance != null) {
                    logger.info("Payload definitions changed; rebuilding {}", PayloadManager.class.getSimpleName());
                }
                instance = new PayloadManager(jsonPayloads);
            }
            return instance;
        }
    }

    /** The parsed payload definitions, or null when they could not be parsed. */
    public List<PayloadTemplate> getPayloadTemplates() {
        return payloadTemplates;
    }

    /**
     * @deprecated This method is incomplete and does not render templates properly.
     * Use {@link FreeMarkerPayloadManager#getPayload(Map, Map)} instead.
     */
    @Deprecated
    public String renderPayload(String payLoadTemplate, Map<String, Object>... templateReplacementMap) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");

        Map<String, Object> userSubstitutionPayloadParamsMap;
        if (templateReplacementMap.length == 1) {
            userSubstitutionPayloadParamsMap = templateReplacementMap[0];
        } else {
            setDefaultPayloadMap();
            userSubstitutionPayloadParamsMap = this.defaultMap;
        }

        Template template = cfg.getTemplate(payLoadTemplate);
        StringWriter writer = new StringWriter();
        template.process(userSubstitutionPayloadParamsMap, writer);

        return writer.toString();
    }

    private void setDefaultPayloadMap() {
        if (this.defaultMap == null) {
            this.defaultMap = new HashMap<>();
        }
    }

    /**
     * @deprecated This method is not implemented.
     * Use {@link FreeMarkerPayloadManager#getPayload(Map, Map)} instead.
     */
    @Deprecated
    public String renderPayload(Object model) {
        logger.warn("renderPayload(Object) is not implemented. Use FreeMarkerPayloadManager instead.");
        return null;
    }

    public Map<String, Object> getPayloadMetaData(Map<String, String> filterMap) {
        return findPayloadMetaData(filterMap);
    }

    public String getPayloadMetaDataAsString(Map<String, String> filterMap) {
        Map<String, Object> result = findPayloadMetaData(filterMap);
        if (result != null) {
            try {
                return objectMapper.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize payload metadata: {}", e.getMessage(), e);
            }
        }
        return null;
    }

    private Map<String, Object> findPayloadMetaData(Map<String, String> filterMap) {
        Filter filter = buildFilter(filterMap);
        List<Map<String, Object>> result = JsonPath.parse(this.payloadsAsJson).read("$[?]", filter);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    private Filter buildFilter(Map<String, String> filterMap) {
        Set<Map.Entry<String, String>> entrySet = filterMap.entrySet();
        int setStart = 1;
        Criteria criteria = null;
        for (Map.Entry<String, String> entry : entrySet) {
            if (setStart == 1) {
                criteria = Criteria.where(entry.getKey()).eq(entry.getValue());
            } else {
                criteria = criteria.and(entry.getKey()).eq(entry.getValue());
            }
            setStart++;
        }
        return Filter.filter(criteria);
    }

    public String getPayloadTemplateAsString(Map<String, Object> payloadMetaData) {
        try {
            return objectMapper.writeValueAsString(payloadMetaData.get("payload"));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize payload template: {}", e.getMessage(), e);
        }
        return null;
    }
}
