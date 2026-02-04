# Web Search MCP Server

A secure Spring Boot MCP (Model Context Protocol) server that provides web search functionality to AI models. Designed for deployment on Cloud Foundry with OAuth2/JWT authentication.

## Features

- **MCP Protocol Support**: SSE (Server-Sent Events) transport for real-time communication
- **Multiple Search Providers**: Brave Search API, SerpAPI, or Google Custom Search
- **Security**: OAuth2 JWT authentication, rate limiting, and secure credential management
- **Cloud Foundry Ready**: Health checks, environment-based configuration, and VCAP_SERVICES support
- **Caching**: Caffeine-based result caching to reduce API calls
- **Rate Limiting**: Per-client request limits to prevent abuse

## MCP Tools

| Tool | Description |
|------|-------------|
| `web_search` | Full web search with configurable result count (returns formatted text) |
| `web_search_json` | Web search returning structured JSON for programmatic processing |
| `quick_search` | Fast search returning top 3 results |

## Prerequisites

- Java 21+
- Maven 3.8+
- A web search API key (Brave, SerpAPI, or Google Custom Search)
- Cloud Foundry CLI (for deployment)

## Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `WEBSEARCH_API_KEY` | API key for your search provider | Yes |
| `WEBSEARCH_PROVIDER` | `BRAVE`, `SERPAPI`, or `GOOGLE_CUSTOM_SEARCH` | No (default: BRAVE) |
| `OAUTH2_JWK_SET_URI` | JWK Set URI for JWT validation | Yes (in cloud profile) |
| `OAUTH2_ISSUER_URI` | OAuth2 issuer URI | Yes (in cloud profile) |
| `PORT` | Server port | No (default: 8080) |

### Search Provider Setup

#### Brave Search API
1. Sign up at [Brave Search API](https://brave.com/search/api/)
2. Set `WEBSEARCH_API_KEY` to your subscription token
3. Set `WEBSEARCH_PROVIDER=BRAVE`

#### SerpAPI
1. Sign up at [SerpAPI](https://serpapi.com/)
2. Set `WEBSEARCH_API_KEY` to your API key
3. Set `WEBSEARCH_PROVIDER=SERPAPI`

#### Google Custom Search
1. Create a Custom Search Engine at [Google Developers](https://developers.google.com/custom-search/v1/overview)
2. Set `WEBSEARCH_API_KEY` to `your-api-key:your-cx-id` (colon-separated)
3. Set `WEBSEARCH_PROVIDER=GOOGLE_CUSTOM_SEARCH`

## Local Development

```bash
# Run with local profile (no auth required)
SPRING_PROFILES_ACTIVE=local \
WEBSEARCH_API_KEY=your-api-key \
./mvnw spring-boot:run
```

The MCP server will be available at:
- SSE Endpoint: `http://localhost:8080/sse`
- Message Endpoint: `http://localhost:8080/mcp/message`
- Health Check: `http://localhost:8080/actuator/health`

## Building

```bash
./mvnw clean package
```

## Cloud Foundry Deployment

### 1. Create/Bind SSO Service

The app auto-configures OAuth2 from Cloud Foundry SSO service bindings (p-identity, sso, uaa, xsuaa):

```bash
# If using Tanzu Application Service Single Sign-On
cf create-service p-identity standard my-sso

# Bind to your app
cf bind-service web-search-mcp my-sso
```

### 2. Update manifest.yml

Edit `manifest.yml` with your SSO service name:

```yaml
applications:
  - name: web-search-mcp
    env:
      WEBSEARCH_API_KEY: your-api-key
    services:
      - my-sso  # Your SSO service instance name
```

### 3. Push to Cloud Foundry

```bash
./mvnw clean package
cf push
```

### Manual OAuth2 Configuration (Alternative)

If not using SSO service binding, set these environment variables:

```bash
cf set-env web-search-mcp OAUTH2_JWK_SET_URI "https://your-auth/.well-known/jwks.json"
cf set-env web-search-mcp OAUTH2_ISSUER_URI "https://your-auth"
cf restage web-search-mcp
```

### Using User-Provided Service for API Key

For production, store API key in a service instead of manifest:

```bash
cf create-user-provided-service web-search-credentials -p '{"api_key":"your-key"}'
cf bind-service web-search-mcp web-search-credentials
cf restage web-search-mcp
```

## Security Features

### OAuth2 JWT Authentication (Cloud Profile)

All MCP endpoints require a valid JWT bearer token:

```bash
curl -H "Authorization: Bearer <your-jwt>" \
     http://your-app.cfapps.io/mcp/message
```

### Rate Limiting

Default: 60 requests per minute per client. Configure via:
- `websearch.rate-limit-per-minute` property
- Returns `429 Too Many Requests` when exceeded

### Health Endpoints (Unauthenticated)

- `/actuator/health/liveness` - Kubernetes/CF liveness probe
- `/actuator/health/readiness` - Kubernetes/CF readiness probe
- `/actuator/health` - Full health status (details require auth)

## Connecting AI Models

### Claude Desktop Configuration

Add to your MCP configuration:

```json
{
  "mcpServers": {
    "web-search": {
      "url": "http://localhost:8080/sse",
      "transport": "sse"
    }
  }
}
```

### Using with Spring AI Client

```java
@Bean
McpClient mcpClient() {
    return McpClient.sync(
        new HttpClientSseClientTransport("http://localhost:8080")
    ).build();
}
```

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   AI Model      │────▶│  MCP Server      │────▶│  Search API     │
│   (Client)      │◀────│  (Spring Boot)   │◀────│  (Brave/etc)    │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                       │
        │ SSE                   │ OAuth2/JWT
        ▼                       ▼
   MCP Protocol          Security Filter
```

## Tool Implementation

Tools are defined using the `@McpTool` annotation from `org.springaicommunity.mcp.annotation`:

```java
@Component
public class WebSearchTools {

    @McpTool(name = "web_search", description = "Search the web for information")
    public String webSearch(
            @McpToolParam(description = "The search query", required = true) String query,
            @McpToolParam(description = "Max results", required = false) Integer maxResults
    ) {
        // Implementation
    }
}
```

## References

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- [MCP Annotations Examples](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-examples.html)
- [Model Context Protocol Specification](https://modelcontextprotocol.github.io/specification/)
- [Brave Search API](https://brave.com/search/api/)
- [SerpAPI](https://serpapi.com/)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Cloud Foundry Java Buildpack](https://docs.cloudfoundry.org/buildpacks/java/)

## License

MIT
