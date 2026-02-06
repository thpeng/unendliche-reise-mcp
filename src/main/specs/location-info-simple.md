# Feature Spec: Location Information Service - Simple

## Overview
MCP tool for searching Swiss public transport locations by name. Wraps OJP 2.0 LocationInformationRequest.

## MCP Tool Definition

**Tool Name**: `findLocation`

**Description** (~40 tokens):
```
Find Swiss public transport stops, addresses, or POIs by name. Returns matching locations with coordinates.
```

**Input Schema** (~80 tokens):
```json
{
  "name": { "type": "string", "description": "Location name to search", "maxLength": 100 },
  "limit": { "type": "integer", "default": 5, "minimum": 1, "maximum": 10 }
}
```

**Output**: List of locations with name, type, coordinates, stopRef (if stop).

## Package Structure

```
ch.thp.proto.unendlichereise/
  └── locationinfo/
      ├── LocationInfoConfig.java      # WebClient, API config
      ├── LocationInfoService.java     # OJP API calls
      ├── LocationInfoTool.java        # @Tool annotated MCP handler
      └── model/
          ├── LocationRequest.java     # Tool input record
          └── LocationResult.java      # Tool output record
```

## Implementation Steps

### Step 1: Create Package and Models
Create `locationinfo` package with input/output records.

```java
// LocationRequest.java
public record LocationRequest(
    @NotBlank @Size(max = 100) String name,
    @Min(1) @Max(10) Integer limit
) {
    public LocationRequest {
        if (limit == null) limit = 5;
    }
}

// LocationResult.java
public record LocationResult(
    String name,
    String type,        // stop, address, poi, topographicPlace
    Double longitude,
    Double latitude,
    String stopRef      // nullable, only for stops
) {}
```

### Step 2: Configuration
Create `LocationInfoConfig.java`:
- Configure WebClient for OJP API (`https://api.opentransportdata.swiss/ojp20`)
- Inject API token from properties (`ojp.api.token`)
- Set Content-Type: `application/xml`
- Set Authorization header with Bearer token

### Step 3: Service Layer
Create `LocationInfoService.java`:
- Build OJP XML request from input parameters
- Call OJP API reactively via WebClient
- Parse XML response, extract PlaceResult elements
- Map to LocationResult records
- Handle errors gracefully (return empty list on API errors)

**OJP Request Template** (from openapi.yaml exampleLIR1):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
  <OJPRequest>
    <siri:ServiceRequest>
      <siri:RequestTimestamp>{timestamp}</siri:RequestTimestamp>
      <siri:RequestorRef>unendliche-reise-mcp</siri:RequestorRef>
      <OJPLocationInformationRequest>
        <siri:RequestTimestamp>{timestamp}</siri:RequestTimestamp>
        <siri:MessageIdentifier>LIR-{uuid}</siri:MessageIdentifier>
        <InitialInput>
          <Name>{searchName}</Name>
        </InitialInput>
        <Restrictions>
          <NumberOfResults>{limit}</NumberOfResults>
        </Restrictions>
      </OJPLocationInformationRequest>
    </siri:ServiceRequest>
  </OJPRequest>
</OJP>
```

### Step 4: Tool Handler
Create `LocationInfoTool.java`:
```java
@Service
public class LocationInfoTool {

    @Tool(description = "Find Swiss public transport stops, addresses, or POIs by name")
    public List<LocationResult> findLocation(LocationRequest request) {
        // Validate input
        // Call service
        // Return results
    }
}
```

### Step 5: Register Tool
Update `UnendlicheReiseApplication.java`:
```java
@Bean
public ToolCallbackProvider locationInfoTools(LocationInfoTool tool) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(tool)
        .build();
}
```

### Step 6: Application Properties
Add to `application.yaml`:
```yaml
ojp:
  api:
    base-url: https://api.opentransportdata.swiss
    token: ${OJP_API_TOKEN:}
```

## Security Considerations

### Input Validation
- Reject names > 100 chars
- Reject names with control characters
- Limit results to max 10
- Sanitize name before XML insertion (escape `<`, `>`, `&`, `"`, `'`)

### Prompt Injection Defense
- Do not echo raw user input in tool description
- Filter instruction-like patterns from input
- Log suspicious inputs (multi-line, "ignore", "you are")

### Error Handling
- Never expose API tokens or internal errors
- Return empty results on upstream failures
- Log errors internally without user-facing details

## Testing Strategy

### Unit Tests
- `LocationInfoServiceTest`: Mock WebClient, test XML building and response parsing
- `LocationInfoToolTest`: Test input validation, verify security filters

### Integration Tests (with mocks)
- Mock OJP API responses
- Test full request/response cycle
- Verify MCP protocol compliance

### Security Tests
- Test XSS/injection in name parameter
- Test oversized inputs
- Test malformed inputs

## Token Budget Estimate

| Component | Tokens |
|-----------|--------|
| Tool name | 5 |
| Description | 40 |
| Input schema | 80 |
| **Total** | ~125 |

Well within 2000 token limit, leaves room for additional tools.

## Response Format for LLM

Example response format (concise, LLM-friendly):
```json
[
  {"name": "Bern", "type": "stop", "longitude": 7.439, "latitude": 46.949, "stopRef": "8507000"},
  {"name": "Bern Bahnhof", "type": "stop", "longitude": 7.440, "latitude": 46.948, "stopRef": "8576646"}
]
```

## Dependencies

No new dependencies required. Uses existing:
- `spring-ai-starter-mcp-server-webflux` (WebClient, @Tool)
- `spring-boot-starter-security` (future auth)
- `lombok` (records enhancement)

## Open Questions

1. Should we filter by place type (stop only vs all)?
   - Recommendation: Start with all types

2. XML parsing strategy?
   - Option B: Simple DOM/StAX parsing for minimal footprint

