package com.example.websearchmcp.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchServiceBufferTest {

    @Test
    void configuredClientReadsAResponseLargerThanSpringDefault() throws Exception {
        byte[] body = ("{\"payload\":\"" + "x".repeat(300 * 1024) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String response = WebSearchService.buildWebClient(4 * 1024 * 1024)
                    .get()
                    .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/search")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            assertThat(response).hasSize(body.length);
        } finally {
            server.stop(0);
        }
    }
}
