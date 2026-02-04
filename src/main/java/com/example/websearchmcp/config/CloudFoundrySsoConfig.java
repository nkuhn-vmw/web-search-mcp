package com.example.websearchmcp.config;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Auto-configures OAuth2 Resource Server from Cloud Foundry SSO service binding.
 *
 * Supports common SSO service types:
 * - p-identity (Tanzu Application Service Single Sign-On)
 * - predix-uaa (Predix UAA)
 * - xsuaa (SAP XSUAA)
 * - uaa (Cloud Foundry UAA)
 */
@Configuration
@Profile("cloud")
@ConditionalOnCloudPlatform(CloudPlatform.CLOUD_FOUNDRY)
public class CloudFoundrySsoConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundrySsoConfig.class);

    private static final List<String> SSO_SERVICE_TAGS = List.of(
            "p-identity", "identity", "sso", "oauth", "uaa", "xsuaa", "predix-uaa"
    );

    @Bean
    @Primary
    public OAuth2ResourceServerProperties oauth2ResourceServerProperties() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();

        try {
            CfEnv cfEnv = new CfEnv();
            CfService ssoService = findSsoService(cfEnv);

            if (ssoService != null) {
                CfCredentials credentials = ssoService.getCredentials();
                configureFromCredentials(properties, credentials);
                log.info("Configured OAuth2 Resource Server from Cloud Foundry SSO service: {}",
                        ssoService.getName());
            } else {
                log.warn("No SSO service binding found. OAuth2 will use application.yml configuration.");
            }
        } catch (Exception e) {
            log.warn("Failed to configure OAuth2 from VCAP_SERVICES: {}. Using application.yml.",
                    e.getMessage());
        }

        return properties;
    }

    private CfService findSsoService(CfEnv cfEnv) {
        // Try to find by common SSO service tags
        for (String tag : SSO_SERVICE_TAGS) {
            try {
                List<CfService> services = cfEnv.findServicesByTag(tag);
                if (!services.isEmpty()) {
                    return services.get(0);
                }
            } catch (Exception ignored) {
                // Continue searching
            }
        }

        // Try to find by service name patterns
        for (CfService service : cfEnv.findAllServices()) {
            String name = service.getName().toLowerCase();
            String label = service.getLabel() != null ? service.getLabel().toLowerCase() : "";

            if (name.contains("sso") || name.contains("identity") || name.contains("uaa") ||
                label.contains("sso") || label.contains("identity") || label.contains("uaa")) {
                return service;
            }
        }

        return null;
    }

    private void configureFromCredentials(OAuth2ResourceServerProperties properties,
                                          CfCredentials credentials) {
        OAuth2ResourceServerProperties.Jwt jwt = properties.getJwt();

        // Try different credential key names used by various SSO providers
        String issuerUri = getFirstNonNull(credentials,
                "issuer_uri", "issuerUri", "issuer", "auth_domain", "authDomain", "uaa_url", "url");

        String jwkSetUri = getFirstNonNull(credentials,
                "jwks_uri", "jwksUri", "jwk_set_uri", "jwkSetUri");

        // If we have an issuer URI, set it
        if (issuerUri != null) {
            // Ensure it's a proper URL
            if (!issuerUri.startsWith("http")) {
                issuerUri = "https://" + issuerUri;
            }
            jwt.setIssuerUri(issuerUri);
            log.info("Set OAuth2 issuer URI: {}", issuerUri);

            // Derive JWK Set URI if not explicitly provided
            if (jwkSetUri == null) {
                // Common patterns for JWK Set URI
                if (issuerUri.endsWith("/")) {
                    jwkSetUri = issuerUri + ".well-known/jwks.json";
                } else {
                    jwkSetUri = issuerUri + "/.well-known/jwks.json";
                }
            }
        }

        if (jwkSetUri != null) {
            if (!jwkSetUri.startsWith("http")) {
                jwkSetUri = "https://" + jwkSetUri;
            }
            jwt.setJwkSetUri(jwkSetUri);
            log.info("Set OAuth2 JWK Set URI: {}", jwkSetUri);
        }

        // Log available credential keys for debugging
        if (log.isDebugEnabled()) {
            log.debug("Available SSO credentials: {}", credentials.getMap().keySet());
        }
    }

    private String getFirstNonNull(CfCredentials credentials, String... keys) {
        for (String key : keys) {
            Object value = credentials.getMap().get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
