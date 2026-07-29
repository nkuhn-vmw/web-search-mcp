package com.example.websearchmcp.tools;

import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchToolAnnotationsTest {

    @Test
    void allPublishedSearchToolsAreReadOnlyAndNonDestructive() {
        Method[] publishedTools = Arrays.stream(WebSearchTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .toArray(Method[]::new);

        assertThat(publishedTools).hasSize(3);
        for (Method method : publishedTools) {
            McpTool.McpAnnotations annotations =
                    method.getAnnotation(McpTool.class).annotations();
            assertThat(annotations.readOnlyHint()).as(method.getName()).isTrue();
            assertThat(annotations.destructiveHint()).as(method.getName()).isFalse();
            assertThat(annotations.idempotentHint()).as(method.getName()).isTrue();
            assertThat(annotations.openWorldHint()).as(method.getName()).isTrue();
        }
    }
}
