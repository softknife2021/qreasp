package com.softknife.data.context;


import com.softknife.exception.ContextKeyIsBlank;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Key/value storage shared between steps of a test.
 *
 * <p>Thread-safe: parallel TestNG methods may read and write the same context. Null values are
 * allowed (a synchronized map rather than a ConcurrentHashMap, which refuses them).
 */
public class Context {
    private final Map<String, Object> context = Collections.synchronizedMap(new HashMap<>());

    public Context setValue(String key, HashMap value) throws ContextKeyIsBlank {
        putIfKeyNotBlank(key, value);
        return this;
    }

    public Context setValue(String key, String value) throws ContextKeyIsBlank {
        putIfKeyNotBlank(key, value);
        return this;
    }

    public Context setValue(String key, Map value) throws ContextKeyIsBlank {
        putIfKeyNotBlank(key, value);
        return this;
    }

    public Context setValue(String key, Object value) throws ContextKeyIsBlank {
        putIfKeyNotBlank(key, value);
        return this;
    }

    public Context setValue(String key, HashMap<String, Object>[] value) throws ContextKeyIsBlank {
        putIfKeyNotBlank(key, value);
        return this;
    }

    public Object getValue(String key) {
        return context.get(key);
    }

    private void putIfKeyNotBlank(String key, Object value) throws ContextKeyIsBlank {
        if(StringUtils.isBlank(key)){
            throw new ContextKeyIsBlank("Context key must not be blank");
        }
        this.context.put(key, value);
    }

}
