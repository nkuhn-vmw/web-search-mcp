package com.example.websearchmcp.tools;

import com.example.websearchmcp.model.SearchResult;
import com.example.websearchmcp.model.SearchResult.SearchResultItem;
import com.example.websearchmcp.service.WebSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSearchToolsTest {

    @Mock
    private WebSearchService searchService;

    private WebSearchTools webSearchTools;

    @BeforeEach
    void setUp() {
        webSearchTools = new WebSearchTools(searchService);
    }

    @Test
    void webSearch_withValidQuery_returnsFormattedResults() {
        SearchResult mockResult = new SearchResult(
                "test query",
                2,
                List.of(
                        new SearchResultItem("Title 1", "https://example1.com", "Description 1", "example1.com", "brave"),
                        new SearchResultItem("Title 2", "https://example2.com", "Description 2", "example2.com", "brave")
                )
        );

        when(searchService.search(eq("test query"), anyInt())).thenReturn(mockResult);

        String result = webSearchTools.webSearch("test query", 10);

        assertThat(result).contains("Search Results for: test query");
        assertThat(result).contains("Title 1");
        assertThat(result).contains("https://example1.com");
        assertThat(result).contains("Description 1");
    }

    @Test
    void webSearch_withEmptyQuery_returnsError() {
        String result = webSearchTools.webSearch("", 10);
        assertThat(result).contains("Error: Search query cannot be empty");
    }

    @Test
    void webSearch_withNullQuery_returnsError() {
        String result = webSearchTools.webSearch(null, 10);
        assertThat(result).contains("Error: Search query cannot be empty");
    }

    @Test
    void webSearchJson_withValidQuery_returnsSearchResult() {
        SearchResult mockResult = new SearchResult(
                "test query",
                1,
                List.of(new SearchResultItem("Title", "https://example.com", "Description", "example.com", "brave"))
        );

        when(searchService.search(eq("test query"), anyInt())).thenReturn(mockResult);

        SearchResult result = webSearchTools.webSearchJson("test query", 5);

        assertThat(result.query()).isEqualTo("test query");
        assertThat(result.totalResults()).isEqualTo(1);
        assertThat(result.results()).hasSize(1);
    }

    @Test
    void quickSearch_returnsMaxThreeResults() {
        SearchResult mockResult = new SearchResult(
                "quick test",
                3,
                List.of(
                        new SearchResultItem("Result 1", "https://r1.com", "Desc 1", "r1.com", "brave"),
                        new SearchResultItem("Result 2", "https://r2.com", "Desc 2", "r2.com", "brave"),
                        new SearchResultItem("Result 3", "https://r3.com", "Desc 3", "r3.com", "brave")
                )
        );

        when(searchService.search(eq("quick test"), eq(3))).thenReturn(mockResult);

        String result = webSearchTools.quickSearch("quick test");

        assertThat(result).contains("Found 3 results");
    }
}
