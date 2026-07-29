package com.example.websearchmcp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CfWebSearchCredentialResolverTest {

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(CfWebSearchCredentialResolver.CREDENTIAL_NAME);
    }

    @Test
    void resolvesExactCredentialFromCredHubInterpolatedBinding() {
        String vcapServices = """
                {
                  "credhub": [{
                    "name": "brave-search-secret",
                    "credentials": {
                      "WEBSEARCH_API_KEY": "BSA-test-value"
                    }
                  }]
                }
                """;

        assertThat(CfWebSearchCredentialResolver.resolve(vcapServices))
                .contains("BSA-test-value");
    }

    @Test
    void ignoresGenericKeysAndUninterpolatedReferences() {
        String vcapServices = """
                {
                  "ai-models": [{
                    "credentials": {"api_key": "wrong-service-key"}
                  }],
                  "credhub": [{
                    "credentials": {
                      "credhub-ref": "/example/shared/brave/api"
                    }
                  }]
                }
                """;

        assertThat(CfWebSearchCredentialResolver.resolve(vcapServices)).isEmpty();
    }

    @Test
    void explicitEnvironmentAlwaysWins() {
        boolean configured = CfWebSearchCredentialResolver.configureFromEnvironment(Map.of(
                "WEBSEARCH_API_KEY", "explicit-key",
                "VCAP_SERVICES", """
                        {"credhub":[{"credentials":{"WEBSEARCH_API_KEY":"bound-key"}}]}
                        """
        ));

        assertThat(configured).isFalse();
        assertThat(System.getProperty("WEBSEARCH_API_KEY")).isNull();
    }

    @Test
    void malformedVcapServicesFailsClosed() {
        assertThat(CfWebSearchCredentialResolver.resolve("{not-json")).isEmpty();
    }
}
