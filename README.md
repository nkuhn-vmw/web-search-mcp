# Web Search MCP Server

A secure Spring Boot MCP (Model Context Protocol) server that provides web search functionality to AI models. Designed for deployment on Cloud Foundry with OAuth2/JWT authentication via SSO service binding.

## Features

- **MCP Protocol Support**: SSE (Server-Sent Events) transport for real-time communication
- **Multiple Search Providers**: Brave Search API, SerpAPI, or Google Custom Search
- **Cloud Foundry SSO Integration**: Auto-configures OAuth2 from p-identity/UAA service bindings
- **Security**: JWT authentication, rate limiting, and secure credential management
- **Cloud Foundry Ready**: Health checks, VCAP_SERVICES support, Java 21

## MCP Tools

| Tool | Description |
|------|-------------|
| `web_search` | Full web search with configurable result count (returns formatted text) |
| `web_search_json` | Web search returning structured JSON for programmatic processing |
| `quick_search` | Fast search returning top 3 results |

## Prerequisites

- Java 21+
- Maven 3.8+
- A web search API key ([Brave Search](https://brave.com/search/api/), [SerpAPI](https://serpapi.com/), or Google Custom Search)
- Cloud Foundry CLI (for deployment)
- Cloud Foundry SSO service (p-identity, UAA, etc.)

---

## Quick Start

### Local Development (No Auth)

```bash
SPRING_PROFILES_ACTIVE=local \
WEBSEARCH_API_KEY=your-api-key \
./mvnw spring-boot:run
```

The server will be available at `http://localhost:8080`

### Test Locally

```bash
# Health check
curl http://localhost:8080/actuator/health

# SSE endpoint (no auth in local mode)
curl http://localhost:8080/sse
```

---

## Cloud Foundry Deployment

### 1. Create/Bind SSO Service

```bash
# Create SSO service (if not exists)
cf create-service p-identity uaa my-sso

# Or use existing SSO service
cf services  # List available services
```

### 2. Deploy the Application

```bash
# Build
./mvnw clean package

# Push without starting
cf push --no-start

# Set API key securely (not in manifest)
cf set-env web-search-mcp WEBSEARCH_API_KEY "your-api-key"

# Start the app
cf start web-search-mcp
```

### 3. Update manifest.yml

Edit `manifest.yml` to bind your SSO service:

```yaml
applications:
  - name: web-search-mcp
    services:
      - my-sso  # Your SSO service instance name
```

---

## Authentication

The MCP endpoints require a valid JWT token from your Cloud Foundry SSO.

### Getting a Token

**Using CF CLI (easiest):**
```bash
TOKEN=$(cf oauth-token | sed 's/bearer //')
echo $TOKEN
```

**Using curl with UAA:**
```bash
# Client credentials flow (if configured)
curl -X POST "https://uaa.sys.your-domain.com/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET"
```

### Making Authenticated Requests

```bash
TOKEN=$(cf oauth-token | sed 's/bearer //')

# Test SSE endpoint
curl -H "Authorization: Bearer $TOKEN" \
     https://your-app.apps.your-domain.com/sse

# Returns: event:endpoint
#          data:/mcp/message?sessionId=<uuid>
```

---

## Using the MCP Server

### 1. Connect to SSE Endpoint

```bash
TOKEN=$(cf oauth-token | sed 's/bearer //')
curl -H "Authorization: Bearer $TOKEN" https://your-app.apps.your-domain.com/sse
```

Response:
```
event:endpoint
data:/mcp/message?sessionId=abc123-def456
```

### 2. Initialize Session

```bash
curl -X POST "https://your-app.apps.your-domain.com/mcp/message?sessionId=abc123-def456" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {"name": "my-client", "version": "1.0"}
    }
  }'
```

### 3. List Available Tools

```bash
curl -X POST "https://your-app.apps.your-domain.com/mcp/message?sessionId=abc123-def456" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}'
```

### 4. Call a Tool

```bash
curl -X POST "https://your-app.apps.your-domain.com/mcp/message?sessionId=abc123-def456" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "web_search",
      "arguments": {
        "query": "Spring Boot MCP server",
        "maxResults": 5
      }
    }
  }'
```

---

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `WEBSEARCH_API_KEY` | Yes | - | API key for search provider |
| `WEBSEARCH_PROVIDER` | No | `BRAVE` | `BRAVE`, `SERPAPI`, or `GOOGLE_CUSTOM_SEARCH` |
| `SPRING_PROFILES_ACTIVE` | No | - | Set to `cloud` for CF, `local` for dev |

### Search Provider Setup

#### Brave Search API (Recommended)
1. Sign up at [Brave Search API](https://brave.com/search/api/)
2. Get your API key from the dashboard
3. Set `WEBSEARCH_API_KEY` and `WEBSEARCH_PROVIDER=BRAVE`

#### SerpAPI
1. Sign up at [SerpAPI](https://serpapi.com/)
2. Set `WEBSEARCH_API_KEY` and `WEBSEARCH_PROVIDER=SERPAPI`

#### Google Custom Search
1. Create a [Custom Search Engine](https://developers.google.com/custom-search/v1/overview)
2. Set `WEBSEARCH_API_KEY=apiKey:searchEngineId` (colon-separated)
3. Set `WEBSEARCH_PROVIDER=GOOGLE_CUSTOM_SEARCH`

---

## Security Features

### OAuth2 JWT Authentication
- Automatically configures from Cloud Foundry SSO service binding
- Supports p-identity, UAA, and other OAuth2 providers
- JWK endpoint auto-discovered from `auth_domain`

### Rate Limiting
- Default: 60 requests per minute per client
- Configurable via `websearch.rate-limit-per-minute`

### Health Endpoints (Unauthenticated)
- `/actuator/health` - Overall health
- `/actuator/health/liveness` - Kubernetes/CF liveness probe
- `/actuator/health/readiness` - Kubernetes/CF readiness probe

---

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   AI Model      │────▶│  MCP Server      │────▶│  Search API     │
│   (Client)      │◀────│  (Spring Boot)   │◀────│  (Brave/etc)    │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                       │
        │ SSE + JWT             │ VCAP_SERVICES
        ▼                       ▼
   MCP Protocol           CF SSO Service
                          (p-identity/UAA)
```

---

## Endpoints

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /sse` | Yes | SSE connection, returns session ID |
| `POST /mcp/message?sessionId=<id>` | Yes | MCP JSON-RPC messages |
| `GET /actuator/health` | No | Health check |
| `GET /actuator/health/liveness` | No | Liveness probe |
| `GET /actuator/health/readiness` | No | Readiness probe |

---

## Tool Implementation

Tools are defined using the `@McpTool` annotation:

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

---

## References

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- [MCP Annotations Examples](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-examples.html)
- [Model Context Protocol Specification](https://modelcontextprotocol.github.io/specification/)
- [Brave Search API](https://brave.com/search/api/)
- [SerpAPI](https://serpapi.com/)
- [Cloud Foundry SSO](https://docs.cloudfoundry.org/concepts/architecture/uaa.html)

## License

MIT
