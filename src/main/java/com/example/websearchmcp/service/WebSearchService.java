package com.example.websearchmcp.service;

import com.example.websearchmcp.config.WebSearchProperties;
import com.example.websearchmcp.model.SearchResult;
import com.example.websearchmcp.model.SearchResult.SearchResultItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private static final String BRAVE_API_BASE = "https://api.search.brave.com/res/v1/web/search";
    private static final String SERPAPI_BASE = "https://serpapi.com/search";

    private final WebClient webClient;
    private final WebSearchProperties properties;

    public WebSearchService(WebSearchProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Cacheable(value = "searchResults", key = "#query + '-' + #count")
    public SearchResult search(String query, int count) {
        if (count <= 0) {
            count = properties.defaultResultCount();
        }
        count = Math.min(count, 100);

        return switch (properties.provider()) {
            case BRAVE -> searchWithBrave(query, count);
            case SERPAPI -> searchWithSerpApi(query, count);
            case GOOGLE_CUSTOM_SEARCH -> searchWithGoogleCustomSearch(query, count);
        };
    }

    private SearchResult searchWithBrave(String query, int count) {
        log.info("Executing Brave search for query: {}", query);

        String uri = UriComponentsBuilder.fromHttpUrl(BRAVE_API_BASE)
                .queryParam("q", query)
                .queryParam("count", count)
                .queryParam("safesearch", "moderate")
                .build()
                .toUriString();

        JsonNode response = webClient.get()
                .uri(uri)
                .header("X-Subscription-Token", properties.apiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return parseBraveResponse(query, response);
    }

    private SearchResult parseBraveResponse(String query, JsonNode response) {
        List<SearchResultItem> items = new ArrayList<>();

        JsonNode webResults = response.path("web").path("results");
        if (webResults.isArray()) {
            for (JsonNode result : webResults) {
                items.add(new SearchResultItem(
                        result.path("title").asText(""),
                        result.path("url").asText(""),
                        result.path("description").asText(""),
                        result.path("display_url").asText(""),
                        "brave"
                ));
            }
        }

        return new SearchResult(query, items.size(), items);
    }

    private SearchResult searchWithSerpApi(String query, int count) {
        log.info("Executing SerpAPI search for query: {}", query);

        String uri = UriComponentsBuilder.fromHttpUrl(SERPAPI_BASE)
                .queryParam("q", query)
                .queryParam("num", count)
                .queryParam("api_key", properties.apiKey())
                .queryParam("engine", "google")
                .build()
                .toUriString();

        JsonNode response = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return parseSerpApiResponse(query, response);
    }

    private SearchResult parseSerpApiResponse(String query, JsonNode response) {
        List<SearchResultItem> items = new ArrayList<>();

        JsonNode organicResults = response.path("organic_results");
        if (organicResults.isArray()) {
            for (JsonNode result : organicResults) {
                items.add(new SearchResultItem(
                        result.path("title").asText(""),
                        result.path("link").asText(""),
                        result.path("snippet").asText(""),
                        result.path("displayed_link").asText(""),
                        "serpapi"
                ));
            }
        }

        return new SearchResult(query, items.size(), items);
    }

    private SearchResult searchWithGoogleCustomSearch(String query, int count) {
        log.info("Executing Google Custom Search for query: {}", query);

        // Note: Google Custom Search requires both API key and CX (search engine ID)
        // The API key in this case should be formatted as "apiKey:cx"
        String[] keyParts = properties.apiKey().split(":");
        if (keyParts.length != 2) {
            throw new IllegalArgumentException("Google Custom Search requires API key in format 'apiKey:cx'");
        }

        String uri = UriComponentsBuilder.fromHttpUrl("https://www.googleapis.com/customsearch/v1")
                .queryParam("q", query)
                .queryParam("num", Math.min(count, 10)) // Google CSE max is 10 per request
                .queryParam("key", keyParts[0])
                .queryParam("cx", keyParts[1])
                .build()
                .toUriString();

        JsonNode response = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return parseGoogleResponse(query, response);
    }

    private SearchResult parseGoogleResponse(String query, JsonNode response) {
        List<SearchResultItem> items = new ArrayList<>();

        JsonNode searchItems = response.path("items");
        if (searchItems.isArray()) {
            for (JsonNode result : searchItems) {
                items.add(new SearchResultItem(
                        result.path("title").asText(""),
                        result.path("link").asText(""),
                        result.path("snippet").asText(""),
                        result.path("displayLink").asText(""),
                        "google"
                ));
            }
        }

        return new SearchResult(query, items.size(), items);
    }
}
