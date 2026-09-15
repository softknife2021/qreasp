package com.softknife.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.softknife.config.GlobalConfig;
import org.aeonbits.owner.ConfigFactory;


/**
 * @author softknife on 10/15/18
 * @project qreasp
 */

public class GlobalResourceManager {

    private final GlobalConfig globalConfig = ConfigFactory.create(GlobalConfig.class, System.getProperties(), System.getenv());
    private final Configuration configuration = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());


    private GlobalResourceManager() {
    }


    /** Lazily created, thread-safe without locking: the JVM initialises the holder class once. */
    private static final class Holder {
        private static final GlobalResourceManager INSTANCE = new GlobalResourceManager();
    }

    public static GlobalResourceManager getInstance() {
        return Holder.INSTANCE;
    }

    public GlobalConfig getGlobalConfig() {
        return globalConfig;
    }


    public Configuration getConfiguration() {
        return configuration;
    }


    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ObjectMapper getYamlObjectMapper() {
        return yamlObjectMapper;
    }
}



