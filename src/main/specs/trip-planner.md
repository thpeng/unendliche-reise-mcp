# Feature Spec: Trip Planner

## Overview
MCP tool for planning journeys between Swiss public transport stops. Wraps OJP 2.0 TripRequest.

## MCP Tool Definition

**Tool Name**: `planTrip`

**Description** (~40 tokens):
```
Plan a journey between two Swiss public transport stops. Returns trip options with legs, transfers, and timing.
```

**Input Schema** (~120 tokens):
```json
{
  "originRef":      { "type": "string", "description": "Origin stop ref from findLocation" },
  "destinationRef": { "type": "string", "description": "Destination stop ref from findLocation" },
  "departureTime":  { "type": "string", "description": "ISO-8601 datetime, e.g. 2025-06-15T08:30:00+02:00. Default: now" },
  "limit":          { "type": "integer", "default": 3, "minimum": 1, "maximum": 5 }
}
```

**Output**: List of trip results with start/end times, duration, transfers, and ordered legs.

## Package Structure

```
ch.thp.proto.unendlichereise/
  └── tripplanner/
      ├── TripPlannerService.java     # OJP API calls
      ├── TripPlannerTool.java        # @Tool annotated MCP handler
      └── model/
          ├── TripRequest.java        # Tool input record
          ├── TripResult.java         # Tool output record
          └── TripLeg.java            # Single leg within a trip
```

## Implementation Steps

### Step 1: Create Package and Models
Create `tripplanner` package with input/output records.

```java
// TripRequest.java
public record TripRequest(
    @NotBlank String originRef,
    @NotBlank String destinationRef,
    String departureTime,       // nullable, defaults to now
    @Min(1) @Max(5) Integer limit
) {
    public TripRequest {
        if (limit == null) limit = 3;
    }
}

// TripLeg.java
public record TripLeg(
    String mode,            // rail, bus, tram, walk, ...
    String serviceName,     // e.g. "IC 1", "S3", null for walk
    String fromName,
    String toName,
    String departure,       // ISO-8601
    String arrival,         // ISO-8601
    String departurePlatform, // nullable
    String arrivalPlatform    // nullable
) {}

// TripResult.java
public record TripResult(
    String startTime,       // ISO-8601
    String endTime,         // ISO-8601
    int durationMinutes,
    int transfers,
    List<TripLeg> legs
) {}
```

### Step 2: Service Layer
Create `TripPlannerService.java`:
- Build OJP XML request from input parameters
- Call OJP API via shared `OjpClient`
- Parse XML response, extract TripResult elements
- Map to TripResult/TripLeg records
- Discard OJP noise: fare, distance, intermediate stops, estimated times, path guidance, situation refs
- Handle errors gracefully (return empty list on API errors)

**OJP Request Template** (based on openapi.yaml exampleTR1):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
  <OJPRequest>
    <siri:ServiceRequest>
      <siri:RequestTimestamp>{timestamp}</siri:RequestTimestamp>
      <siri:RequestorRef>unendliche-reise-mcp</siri:RequestorRef>
      <OJPTripRequest>
        <siri:RequestTimestamp>{timestamp}</siri:RequestTimestamp>
        <siri:MessageIdentifier>TR-{uuid}</siri:MessageIdentifier>
        <Origin>
          <PlaceRef>
            <siri:StopPointRef>{originRef}</siri:StopPointRef>
          </PlaceRef>
          <!-- DepArrTime only if departureTime provided -->
          <DepArrTime>{departureTime}</DepArrTime>
        </Origin>
        <Destination>
          <PlaceRef>
            <siri:StopPointRef>{destinationRef}</siri:StopPointRef>
          </PlaceRef>
        </Destination>
        <Params>
          <NumberOfResults>{limit}</NumberOfResults>
        </Params>
      </OJPTripRequest>
    </siri:ServiceRequest>
  </OJPRequest>
</OJP>
```

**Response Parsing** - extract from each `<TripResult>`:
- `<Trip>` → one TripResult
  - `<StartTime>`, `<EndTime>` → startTime, endTime
  - `<Duration>` (ISO-8601 duration) → durationMinutes
  - `<Transfers>` → transfers
  - `<Leg>` elements → ordered TripLeg list:
    - `<TimedLeg>` (public transport):
      - Mode from `<Service><Mode><PtMode>` → mode
      - Service name from `<Service><PublishedServiceName><Text>` → serviceName
      - Board stop from `<LegBoard>`: `<StopPointName><Text>`, `<ServiceDeparture><TimetabledTime>`, `<PlannedQuay><Text>`
      - Alight stop from `<LegAlight>`: `<StopPointName><Text>`, `<ServiceArrival><TimetabledTime>`, `<PlannedQuay><Text>`
    - `<TransferLeg>` (walking):
      - mode = "walk"
      - serviceName = null
      - From/to from `<LegStart>/<LegEnd>` → `<StopPointName><Text>`
      - Times from `<TimeWindowStart>/<TimeWindowEnd>`

### Step 3: Tool Handler
Create `TripPlannerTool.java`:
```java
@Service
public class TripPlannerTool {

    @Tool(description = "Plan a journey between two Swiss public transport stops")
    public List<TripResult> planTrip(String originRef, String destinationRef,
                                      String departureTime, Integer limit) {
        // Validate stop refs (whitelist pattern)
        // Validate departure time (ISO-8601 regex) if provided
        // Call service
        // Return results
    }
}
```

### Step 4: Register Tool
Update `UnendlicheReiseApplication.java`:
```java
@Bean
public ToolCallbackProvider tripPlannerTools(TripPlannerTool tool) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(tool)
        .build();
}
```

## Security Considerations

### Input Validation
- **Stop refs**: Whitelist pattern `[a-zA-Z0-9:]+`, reject others
- **Departure time**: Validate with ISO-8601 regex `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$`
- **Limit**: Clamp between 1 and 5
- Sanitize all string inputs via `InputSanitizer` before XML insertion
- XML-escape values before template insertion

### Prompt Injection Defense
- Do not echo raw user input in tool description
- Filter instruction-like patterns from input
- Log suspicious inputs (multi-line, "ignore", "you are")

### Error Handling
- Never expose API tokens or internal errors
- Return empty results on upstream failures
- Log errors internally without user-facing details

## Testing Strategy

### Unit Tests - `TripPlannerToolTest` (Mockito)
- Null/blank stop ref handling → empty list
- Stop ref validation: reject `"8507000; DROP TABLE"`, accept `"8507000"`, `"ch:1:sloid:3000"`
- Departure time validation: reject `"not-a-date"`, accept `"2025-06-15T08:30:00+02:00"`, accept null (defaults to now)
- Limit clamping (min=1, max=5, default=3)
- Prompt injection detection in stop refs
- Verify service called with correct TripRequest

### Integration Tests - `TripPlannerServiceTest` (MockWebServer)
- Valid response with single trip → parsed correctly
- Valid response with multiple trips and legs → all parsed
- Walking transfer legs parsed as mode "walk"
- Duration ISO-8601 parsed to minutes
- Platform extraction (present and absent)
- HTTP 500 → empty list
- Empty/malformed response → empty list
- Request XML contains correct stop refs and departure time
- Request XML omits DepArrTime when departureTime is null

### Security Tests
- Stop ref with injection characters rejected
- Departure time with injection characters rejected

## Token Budget Estimate

| Component | Tokens |
|-----------|--------|
| Tool name | 5 |
| Description | 40 |
| Input schema | 120 |
| **Total** | ~165 |

Combined with findLocation (~125), total ~290. Well within 2000 token limit.

## Response Format for LLM

Example response format (concise, LLM-friendly):
```json
[
  {
    "startTime": "2025-06-15T08:33:00+02:00",
    "endTime": "2025-06-15T09:21:00+02:00",
    "durationMinutes": 48,
    "transfers": 1,
    "legs": [
      {
        "mode": "rail",
        "serviceName": "S12",
        "fromName": "Kloten",
        "toName": "Winterthur",
        "departure": "2025-06-15T08:33:00+02:00",
        "arrival": "2025-06-15T08:55:00+02:00",
        "departurePlatform": "4",
        "arrivalPlatform": "1"
      },
      {
        "mode": "rail",
        "serviceName": "IC 5",
        "fromName": "Winterthur",
        "toName": "Schaffhausen",
        "departure": "2025-06-15T09:01:00+02:00",
        "arrival": "2025-06-15T09:21:00+02:00",
        "departurePlatform": "3",
        "arrivalPlatform": "5"
      }
    ]
  }
]
```

## Dependencies

No new dependencies required. Uses existing:
- `spring-ai-starter-mcp-server-webflux` (WebClient, @Tool)
- Shared `OjpClient` and `InputSanitizer`
- `lombok` (records enhancement)

## Open Questions

1. Should we support arrival time (instead of departure)?
   - Recommendation: onlay departure

2. Should walking legs include duration?
   - Recommendation: Duration is already implicit from start/end times; keep it simple
