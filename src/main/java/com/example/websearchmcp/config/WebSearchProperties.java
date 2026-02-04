package com.example.websearchmcp.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "websearch")
@Validated
public record WebSearchProperties(
        @NotBlank(message = "API key is required")
        String apiKey,

        Provider provider,

        @Min(1) @Max(100)
        int defaultResultCount,

        @Min(1) @Max(300)
        int rateLimitPerMinute,

        @Min(1) @Max(86400)
        int cacheExpirationSeconds
) {
    public WebSearchProperties {
        if (provider == null) {
            provider = Provider.BRAVE;
        }
        if (defaultResultCount == 0) {
            defaultResultCount = 10;
        }
        if (rateLimitPerMinute == 0) {
            rateLimitPerMinute = 60;
        }
        if (cacheExpirationSeconds == 0) {
            cacheExpirationSeconds = 300;
        }
    }

    public enum Provider {
        BRAVE,
        SERPAPI,
        GOOGLE_CUSTOM_SEARCH
    }
}
