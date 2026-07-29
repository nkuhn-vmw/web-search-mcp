package com.example.websearchmcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves only the exact web-search credential from Cloud Foundry service
 * bindings. This supports CredHub service broker interpolation without
 * accepting generic api_key fields from unrelated bound services.
 */
public final class CfWebSearchCredentialResolver {

    static final String CREDENTIAL_NAME = "WEBSEARCH_API_KEY";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CfWebSearchCredentialResolver() {
    }

    public static boolean configureFromEnvironment(Map<String, String> environment) {
        if (hasText(environment.get(CREDENTIAL_NAME))
                || hasText(System.getProperty(CREDENTIAL_NAME))) {
            return false;
        }

        Optional<String> credential = resolve(environment.get("VCAP_SERVICES"));
        credential.ifPresent(value -> System.setProperty(CREDENTIAL_NAME, value));
        return credential.isPresent();
    }

    static Optional<String> resolve(String vcapServices) {
        if (!hasText(vcapServices)) {
            return Optional.empty();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(vcapServices);
            if (!root.isObject()) {
                return Optional.empty();
            }

            Iterator<JsonNode> offerings = root.elements();
            while (offerings.hasNext()) {
                JsonNode bindings = offerings.next();
                if (!bindings.isArray()) {
                    continue;
                }
                for (JsonNode binding : bindings) {
                    JsonNode credential = binding.path("credentials").path(CREDENTIAL_NAME);
                    if (credential.isTextual() && hasText(credential.textValue())) {
                        return Optional.of(credential.textValue());
                    }
                }
            }
        } catch (Exception ignored) {
            // Let normal configuration validation report the missing key.
        }
        return Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
