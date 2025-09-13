package com.rewardmanagement.reward_management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for proper JSON serialization.
 * Handles Java 8 time types like LocalDateTime.
 * 
 * @author Reward Management System
 * @version 1.0
 * @since 2024
 */
@Configuration
public class JacksonConfig {

    /**
     * Configures ObjectMapper with proper Java 8 time support.
     * 
     * @return Configured ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JSR310 module for Java 8 time support
        mapper.registerModule(new JavaTimeModule());
        
        // Configure serialization settings
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        
        // Configure deserialization settings to handle type changes
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        
        return mapper;
    }
}
