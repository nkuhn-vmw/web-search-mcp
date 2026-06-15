package com.example.websearchmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

// Exclude Boot's OAuth2 resource-server auto-config: SecurityConfig owns the
// JwtDecoder and resource-server wiring explicitly (only when security is
// enabled). Leaving the auto-config on makes it try to build a JwtDecoder from
// an empty jwk-set-uri when security is disabled, crashing startup.
@SpringBootApplication(exclude = OAuth2ResourceServerAutoConfiguration.class)
@EnableCaching
public class WebSearchMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSearchMcpApplication.class, args);
    }
}
