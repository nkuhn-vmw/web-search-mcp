# Web Search MCP Server

A secure Spring Boot MCP (Model Context Protocol) server that provides web search functionality to AI models. Designed for deployment on Cloud Foundry with OAuth2/JWT authentication via SSO service binding.

## Features

- **MCP Protocol Support**: Streamable HTTP transport for real-time communication
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

# MCP endpoint - Initialize (no auth in local mode)
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {"name": "test-client", "version": "1.0"}
    }
  }'
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

<!-- ============================================================
     LONG-LIVED TOKEN SETUP
     Use this for service accounts or automated integrations
     that need tokens that don't expire frequently
     ============================================================ -->

### Creating a Long-Lived Token (Service Account)

For automated integrations or service accounts, you can create a UAA client with a long-lived token (e.g., 10 years):

**Prerequisites:**
- Ruby installed (`ruby --version`)
- UAA admin client secret (from Ops Manager > TAS tile > Credentials > UAA Admin Client)

**Step 1: Install the UAA CLI**
```bash
gem install cf-uaac
```

**Step 2: Target and authenticate to UAA**
```bash
# Target your UAA
uaac target https://uaa.sys.your-domain.com --skip-ssl-validation

# Login with admin client (get secret from Ops Manager)
uaac token client get admin -s <ADMIN_CLIENT_SECRET>
```

**Step 3: Create a long-lived client**
```bash
# Create client with 10-year token validity (315360000 seconds)
uaac client add mcp-service-account \
  --name mcp-service-account \
  --secret "your-secure-secret-here" \
  --authorized_grant_types client_credentials \
  --authorities openid,uaa.resource \
  --access_token_validity 315360000
```

**Step 4: Get a token with your new client**
```bash
# Authenticate as the new client
uaac token client get mcp-service-account -s "your-secure-secret-here"

# View the token
uaac context

# Or get just the token value
TOKEN=$(uaac context | grep access_token | awk '{print $2}')
echo $TOKEN
```

**Alternative: Get token via curl**
```bash
TOKEN=$(curl -sk -X POST "https://uaa.sys.your-domain.com/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=mcp-service-account&client_secret=your-secure-secret-here" \
  | jq -r '.access_token')

echo $TOKEN
```

<!-- END LONG-LIVED TOKEN SETUP -->

### Making Authenticated Requests

```bash
TOKEN=$(cf oauth-token | sed 's/bearer //')

# Initialize MCP session
curl -X POST "https://your-app.apps.your-domain.com/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
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

---

## Using the MCP Server

With Streamable HTTP, all MCP communication happens via a single `POST /mcp` endpoint. No separate SSE connection is needed — the server uses session IDs returned in the `Mcp-Session-Id` response header to maintain state.

### 1. Initialize Session

```bash
TOKEN=$(cf oauth-token | sed 's/bearer //')

curl -X POST "https://your-app.apps.your-domain.com/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
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

Save the `Mcp-Session-Id` header from the response for subsequent requests.

### 2. List Available Tools

```bash
curl -X POST "https://your-app.apps.your-domain.com/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <session-id-from-init>" \
  -d '{"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}'
```

### 3. Call a Tool

```bash
curl -X POST "https://your-app.apps.your-domain.com/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <session-id-from-init>" \
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
        │ HTTP + JWT            │ VCAP_SERVICES
        ▼                       ▼
   MCP Protocol           CF SSO Service
   (Streamable HTTP)
                          (p-identity/UAA)
```

---

## Endpoints

| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /mcp` | Yes | Streamable HTTP MCP endpoint (all JSON-RPC messages) |
| `GET /mcp` | Yes | SSE stream for server-initiated notifications |
| `DELETE /mcp` | Yes | Terminate MCP session |
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
