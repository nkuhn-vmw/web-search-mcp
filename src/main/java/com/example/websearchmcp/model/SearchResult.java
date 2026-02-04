package com.example.websearchmcp.model;

import java.util.List;

public record SearchResult(
        String query,
        int totalResults,
        List<SearchResultItem> results
) {
    public record SearchResultItem(
            String title,
            String url,
            String description,
            String displayUrl,
            String source
    ) {}
}
