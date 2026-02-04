package com.example.websearchmcp.tools;

import com.example.websearchmcp.model.SearchResult;
import com.example.websearchmcp.service.WebSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    private final WebSearchService searchService;

    public WebSearchTools(WebSearchService searchService) {
        this.searchService = searchService;
    }

    @McpTool(name = "web_search", description = "Search the web for information. Returns a list of relevant web pages with titles, URLs, and descriptions. Use this tool when you need to find current information, facts, or resources from the internet.")
    public String webSearch(
            @McpToolParam(description = "The search query string. Be specific and use relevant keywords for better results.", required = true) String query,
            @McpToolParam(description = "Maximum number of results to return. Default is 10, maximum is 100.", required = false) Integer maxResults
    ) {
        log.info("MCP web_search tool invoked with query: {}", query);

        if (query == null || query.isBlank()) {
            return "Error: Search query cannot be empty";
        }

        try {
            int count = (maxResults != null && maxResults > 0) ? maxResults : 10;
            SearchResult result = searchService.search(query, count);

            return formatSearchResults(result);
        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return "Error performing search: " + e.getMessage();
        }
    }

    @McpTool(name = "web_search_json", description = "Search the web and return results as structured JSON. Use this when you need to programmatically process search results.")
    public SearchResult webSearchJson(
            @McpToolParam(description = "The search query string", required = true) String query,
            @McpToolParam(description = "Maximum number of results to return (default: 10, max: 100)", required = false) Integer maxResults
    ) {
        log.info("MCP web_search_json tool invoked with query: {}", query);

        if (query == null || query.isBlank()) {
            return new SearchResult(query, 0, java.util.List.of());
        }

        int count = (maxResults != null && maxResults > 0) ? maxResults : 10;
        return searchService.search(query, count);
    }

    @McpTool(name = "quick_search", description = "Perform a quick web search returning only the top 3 most relevant results. Ideal for quick fact-checking or when you need just a few authoritative sources.")
    public String quickSearch(
            @McpToolParam(description = "The search query", required = true) String query
    ) {
        log.info("MCP quick_search tool invoked with query: {}", query);

        if (query == null || query.isBlank()) {
            return "Error: Search query cannot be empty";
        }

        try {
            SearchResult result = searchService.search(query, 3);
            return formatSearchResults(result);
        } catch (Exception e) {
            log.error("Quick search failed for query: {}", query, e);
            return "Error performing search: " + e.getMessage();
        }
    }

    private String formatSearchResults(SearchResult result) {
        if (result.results().isEmpty()) {
            return "No results found for: " + result.query();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Search Results for: ").append(result.query()).append("\n");
        sb.append("Found ").append(result.totalResults()).append(" results:\n\n");

        int index = 1;
        for (SearchResult.SearchResultItem item : result.results()) {
            sb.append(index++).append(". **").append(item.title()).append("**\n");
            sb.append("   URL: ").append(item.url()).append("\n");
            if (item.description() != null && !item.description().isBlank()) {
                sb.append("   ").append(item.description()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
