package com.example.websearchmcp.security;

import com.example.websearchmcp.config.WebSearchProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final Cache<String, AtomicInteger> requestCounts;
    private final int maxRequestsPerMinute;

    public RateLimitingFilter(WebSearchProperties properties) {
        this.maxRequestsPerMinute = properties.rateLimitPerMinute();
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/mcp") && !request.getRequestURI().startsWith("/sse")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIdentifier = extractClientIdentifier(request);
        AtomicInteger count = requestCounts.get(clientIdentifier, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > maxRequestsPerMinute) {
            log.warn("Rate limit exceeded for client: {}", clientIdentifier);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIdentifier(HttpServletRequest request) {
        // Try to get user principal from OAuth2 token
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }

        // Fall back to X-Forwarded-For header (common in Cloud Foundry)
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        // Last resort: use remote address
        return request.getRemoteAddr();
    }
}
