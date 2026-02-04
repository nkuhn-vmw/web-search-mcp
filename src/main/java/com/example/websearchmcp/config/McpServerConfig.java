package com.example.websearchmcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebSearchProperties.class)
public class McpServerConfig {
    // Configuration properties are automatically bound via @EnableConfigurationProperties
}
