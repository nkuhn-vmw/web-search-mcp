package com.example.websearchmcp.security;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    @Profile("cloud")
    @ConditionalOnCloudPlatform(CloudPlatform.CLOUD_FOUNDRY)
    public SecurityFilterChain cloudSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/mcp/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));

        return http.build();
    }

    @Bean
    @Profile("cloud")
    @ConditionalOnCloudPlatform(CloudPlatform.CLOUD_FOUNDRY)
    public JwtDecoder cloudJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String configuredJwkSetUri) {

        String jwkSetUri = configuredJwkSetUri;

        // Try to get from SSO service binding if not configured
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            jwkSetUri = getJwkSetUriFromVcapServices();
        }

        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalStateException(
                    "No JWK Set URI found. Either bind an SSO service or set OAUTH2_JWK_SET_URI");
        }

        log.info("Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    private String getJwkSetUriFromVcapServices() {
        try {
            CfEnv cfEnv = new CfEnv();

            // Look for p-identity or UAA service
            List<String> serviceLabels = List.of("p-identity", "uaa", "identity");

            for (String label : serviceLabels) {
                try {
                    CfService service = cfEnv.findServiceByLabel(label);
                    if (service != null) {
                        return extractJwkSetUri(service.getCredentials());
                    }
                } catch (Exception ignored) {
                }
            }

            // Try by tag
            for (String tag : List.of("sso", "oauth", "identity", "uaa")) {
                try {
                    List<CfService> services = cfEnv.findServicesByTag(tag);
                    if (!services.isEmpty()) {
                        return extractJwkSetUri(services.get(0).getCredentials());
                    }
                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {
            log.warn("Failed to read VCAP_SERVICES: {}", e.getMessage());
        }

        return null;
    }

    private String extractJwkSetUri(CfCredentials credentials) {
        // Try explicit jwks_uri first
        String jwkSetUri = getCredential(credentials, "jwks_uri", "jwksUri", "jwk_set_uri");
        if (jwkSetUri != null) {
            return jwkSetUri;
        }

        // Derive from auth_domain (common for p-identity/UAA)
        String authDomain = getCredential(credentials, "auth_domain", "authDomain", "issuer_uri", "issuerUri", "uaa_url");
        if (authDomain != null) {
            if (!authDomain.startsWith("http")) {
                authDomain = "https://" + authDomain;
            }
            // UAA/p-identity uses /token_keys endpoint
            String uri = authDomain.endsWith("/") ? authDomain + "token_keys" : authDomain + "/token_keys";
            log.info("Derived JWK Set URI from auth_domain: {}", uri);
            return uri;
        }

        return null;
    }

    private String getCredential(CfCredentials credentials, String... keys) {
        for (String key : keys) {
            Object value = credentials.getMap().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    @Bean
    @Profile("!cloud")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/mcp/**").permitAll()
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
