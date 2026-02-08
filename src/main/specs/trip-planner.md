# Feature Spec: Trip Planner (v2 — server-side resolution)

## Overview
MCP tool for planning journeys between Swiss public transport stops. The user provides **human-readable location names** (e.g. "Bern", "Zürich HB"). The server resolves them to stop refs internally via the OJP LocationInformation API, then issues an OJP 2.0 TripRequest. If a name is ambiguous (multiple matches), the server uses **MCP elicitation** to let the user pick the correct stop.

### Key change from v1
v1 required the LLM to call `findLocation` first, then pass brittle stop refs into `planTrip`. v2 moves resolution server-side, making the tool self-contained and robust.

## MCP Tool Definition

**Tool Name**: `planTrip`

**Description** (~40 tokens):
```
Plan a journey between two Swiss public transport stops by name. Resolves locations automatically and returns trip options with legs, transfers, and timing.
```

**Input Schema** (~100 tokens):
```json
{
  "origin":         { "type": "string", "description": "Origin stop name, e.g. 'Bern' or 'Zürich HB'" },
  "destination":    { "type": "string", "description": "Destination stop name, e.g. 'Basel SBB'" },
  "departureTime":  { "type": "string", "description": "ISO-8601 datetime, e.g. 2025-06-15T08:30:00+02:00. Default: now" },
  "limit":          { "type": "integer", "default": 3, "minimum": 1, "maximum": 5 }
}
```

**Output**: List of trip results with start/end times, duration, transfers, and ordered legs — or a descriptive error message if resolution fails.

## Package Structure

```
ch.thp.proto.unendlichereise/
  └── tripplanner/
      ├── TripPlannerService.java       # OJP TripRequest API calls
      ├── TripPlannerTool.java          # @McpTool annotated MCP handler (with elicitation)
      ├── LocationResolver.java         # Resolves name → stop ref (wraps LocationInfoService)
      └── model/
          ├── TripRequest.java          # Internal request record (uses resolved stop refs)
          ├── TripResult.java           # Tool output record
          ├── TripLeg.java              # Single leg within a trip
          └── ResolvedStop.java         # Resolved stop (name + ref)
```

## Implementation Steps

### Step 1: Models

```java
// ResolvedStop.java — result of name-to-ref resolution
public record ResolvedStop(
    String name,        // display name from OJP, e.g. "Bern"
    String stopRef      // e.g. "8507000" or "ch:1:sloid:3000"
) {}

// TripRequest.java — internal, always uses resolved refs
public record TripRequest(
    String originRef,
    String destinationRef,
    String departureTime,       // nullable, defaults to now
    @Min(1) @Max(5) Integer limit
) {
    public TripRequest {
        if (limit == null) limit = 3;
    }
}

// TripLeg.java — unchanged from v1
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

// TripResult.java — unchanged from v1
public record TripResult(
    String startTime,       // ISO-8601
    String endTime,         // ISO-8601
    int durationMinutes,
    int transfers,
    List<TripLeg> legs
) {}
```

### Step 2: LocationResolver

New component that wraps `LocationInfoService` and handles disambiguation.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LocationResolver {

    private final LocationInfoService locationInfoService;

    /**
     * Resolve a human-readable name to a single stop.
     * Returns a list of candidates — caller decides how to disambiguate.
     */
    public List<ResolvedStop> resolve(String name) {
        LocationRequest request = new LocationRequest(name, 5);
        return locationInfoService.findLocations(request).stream()
                .filter(r -> r.stopRef() != null)   // only stops, not addresses/POIs
                .map(r -> new ResolvedStop(r.name(), r.stopRef()))
                .toList();
    }
}
```

### Step 3: Tool Handler — `@McpTool` with elicitation

Switch from `@Tool` to `@McpTool` to gain access to `McpSyncRequestContext` for elicitation.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TripPlannerTool {

    private final TripPlannerService tripPlannerService;
    private final LocationResolver locationResolver;
    private final InputSanitizer inputSanitizer;

    @McpTool(name = "planTrip",
             description = "Plan a journey between two Swiss public transport stops by name. "
                         + "Resolves locations automatically and returns trip options with legs, transfers, and timing.")
    public Object planTrip(
            McpSyncRequestContext context,
            @McpToolParam(description = "Origin stop name, e.g. 'Bern'", required = true) String origin,
            @McpToolParam(description = "Destination stop name, e.g. 'Basel SBB'", required = true) String destination,
            @McpToolParam(description = "ISO-8601 datetime, e.g. 2025-06-15T08:30:00+02:00. Default: now") String departureTime,
            @McpToolParam(description = "Max results 1-5, default 3") Integer limit
    ) {
        // --- validate & sanitize ---
        String sanitizedOrigin = inputSanitizer.sanitize(origin);
        String sanitizedDestination = inputSanitizer.sanitize(destination);
        if (sanitizedOrigin == null || sanitizedOrigin.isBlank()
                || sanitizedDestination == null || sanitizedDestination.isBlank()) {
            return Map.of("error", "Origin and destination must not be empty.");
        }

        if (departureTime != null && !DEPARTURE_TIME_PATTERN.matcher(departureTime).matches()) {
            return Map.of("error", "Invalid departure time format. Expected: 2025-06-15T08:30:00+02:00");
        }

        int effectiveLimit = clampLimit(limit);

        // --- resolve origin ---
        ResolvedStop resolvedOrigin = resolveStop(context, sanitizedOrigin, "origin");
        if (resolvedOrigin == null) return Map.of("error", "Could not resolve origin: " + sanitizedOrigin);

        // --- resolve destination ---
        ResolvedStop resolvedDest = resolveStop(context, sanitizedDestination, "destination");
        if (resolvedDest == null) return Map.of("error", "Could not resolve destination: " + sanitizedDestination);

        // --- plan trip ---
        TripRequest request = new TripRequest(
                resolvedOrigin.stopRef(), resolvedDest.stopRef(),
                departureTime, effectiveLimit);
        return tripPlannerService.planTrips(request);
    }

    /**
     * Resolve a name to a single stop.
     * - 0 candidates  → return null (not found)
     * - 1 candidate   → return it directly
     * - N candidates  → use elicitation to let user pick, fall back to first match
     */
    private ResolvedStop resolveStop(McpSyncRequestContext context, String name, String role) {
        List<ResolvedStop> candidates = locationResolver.resolve(name);

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // --- ambiguous: try elicitation ---
        if (context.elicitEnabled()) {
            return disambiguateViaElicitation(context, candidates, role);
        }

        // fallback: use first (best-ranked) candidate
        log.info("Ambiguous {} '{}' with {} candidates, using first match: {}",
                role, name, candidates.size(), candidates.get(0).name());
        return candidates.get(0);
    }

    private ResolvedStop disambiguateViaElicitation(
            McpSyncRequestContext context,
            List<ResolvedStop> candidates,
            String role
    ) {
        // Build a message listing the options
        String optionList = IntStream.range(0, candidates.size())
                .mapToObj(i -> (i + 1) + ". " + candidates.get(i).name())
                .collect(Collectors.joining("\n"));

        String message = "Multiple %s stops found. Please pick one:\n%s".formatted(role, optionList);

        try {
            StructuredElicitResult<StopChoice> result = context.elicit(
                    e -> e.message(message),
                    StopChoice.class
            );

            if (result.action() == ElicitResult.Action.ACCEPT) {
                int choice = result.structuredContent().choice();
                if (choice >= 1 && choice <= candidates.size()) {
                    return candidates.get(choice - 1);
                }
            }
        } catch (Exception e) {
            log.warn("Elicitation failed for {}, falling back to first candidate", role, e);
        }

        return candidates.get(0);
    }

    /** Record used for elicitation — user provides a number to pick a stop. */
    public record StopChoice(int choice) {}
}
```

### Step 4: TripPlannerService — unchanged from v1

The service layer stays the same — it accepts `TripRequest` with resolved stop refs and builds/parses OJP XML. No changes needed.

### Step 5: Register in Application

```java
@Bean
public ToolCallbackProvider tripPlannerTools(TripPlannerTool tool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(tool)
            .build();
}
```

> **Note**: Check whether `MethodToolCallbackProvider` works with `@McpTool` or if `McpToolCallbackProvider` is needed. The Spring AI docs use `@McpTool` with component scanning — the bean registration approach may differ.

## Resolution Flow

```
User: "I want to go from Bern to Zürich at 8:30"
  │
  ▼
LLM calls: planTrip(origin="Bern", destination="Zürich", departureTime="...T08:30:00+02:00")
  │
  ▼
Server: LocationResolver.resolve("Bern")
  → OJP LocationInformation → 1 result → ResolvedStop("Bern", "8507000") ✓
  │
  ▼
Server: LocationResolver.resolve("Zürich")
  → OJP LocationInformation → 3 results:
      1. Zürich HB (8503000)
      2. Zürich Flughafen (8503016)
      3. Zürich Oerlikon (8503006)
  │
  ▼ (ambiguous)
Server: context.elicit("Multiple destination stops found...")
  → User picks: 1 (Zürich HB)
  │
  ▼
Server: TripPlannerService.planTrips(originRef="8507000", destRef="8503000", ...)
  → OJP TripRequest → parse → return List<TripResult>
```

### Fallback when elicitation is unavailable

If the MCP client doesn't support elicitation (stateless mode), the server uses the **first candidate** (best-ranked by OJP). This is a reasonable default since OJP ranks results by relevance. The tool result should include a note about which stop was used:

```java
// In the fallback path, wrap the result:
Map.of(
    "note", "Resolved '%s' to '%s'. If this is wrong, try a more specific name.".formatted(name, candidate.name()),
    "trips", tripResults
)
```

## Security Considerations

### Input Validation
- **Origin/destination names**: Sanitize via `InputSanitizer` (control chars, prompt injection patterns)
- **Name length**: Truncate to 100 chars via `InputSanitizer.truncate()`
- **Departure time**: Validate with ISO-8601 regex `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$`
- **Limit**: Clamp between 1 and 5
- **Stop refs** (internal): Never exposed to the user; only produced by server-side resolution
- XML-escape all values before template insertion

### Prompt Injection Defense
- Same as v1: InputSanitizer filters suspicious patterns, logs them
- Stop refs are never user-supplied, eliminating the injection surface from v1

### Error Handling
- Never expose API tokens or internal errors
- Return structured error maps (not exceptions) for user-facing problems
- Log errors internally

## Testing Strategy

### Unit Tests — `LocationResolverTest` (Mockito)
- Name with 1 match → returns single candidate
- Name with multiple matches → returns all candidates (only stops, not addresses)
- Name with 0 matches → returns empty list
- Name matching addresses/POIs but no stops → returns empty list

### Unit Tests — `TripPlannerToolTest` (Mockito)
- Valid input, single-match resolution → calls service, returns results
- Null/blank origin → returns error map, no service call
- Null/blank destination → returns error map, no service call
- Invalid departureTime → returns error map
- Limit null → default 3, limit 0 → clamped to 1, limit 99 → clamped to 5
- Origin ambiguous + elicitation enabled → elicit called, user picks, service called with correct ref
- Origin ambiguous + elicitation disabled → first candidate used, service called
- Origin not found (0 candidates) → returns error map
- Destination ambiguous + user rejects elicitation → first candidate used
- Elicitation throws exception → falls back to first candidate

### Integration Tests — `TripPlannerServiceTest` (MockWebServer) — unchanged from v1
- Single trip with 2 timed legs → parsed correctly
- Multiple trips → all returned
- Transfer/walking leg → mode="walk"
- Duration PT48M → durationMinutes=48
- Missing platform → null
- No departureTime → no `<DepArrTime>` in XML
- With departureTime → `<DepArrTime>` present
- HTTP 500 → empty list
- Empty response → empty list
- Request XML contains correct stop refs

## Token Budget Estimate

| Component | Tokens |
|-----------|--------|
| Tool name | 5 |
| Description | 45 |
| Input schema | 100 |
| **Total** | ~150 |

Slightly smaller than v1 (no stopRef descriptions). Combined with findLocation (~125), total ~275.

## Dependencies

No new dependencies required. Uses existing:
- `spring-ai-starter-mcp-server-webflux` (`@McpTool`, `McpSyncRequestContext`, WebClient)
- Shared `OjpClient`, `InputSanitizer`, `LocationInfoService`
- `lombok`

### Migration checklist (from v1 → v2)
- [ ] Switch `@Tool` → `@McpTool`, `@ToolParam` → `@McpToolParam`
- [ ] Verify `MethodToolCallbackProvider` works with `@McpTool` (or switch to annotation scanning)
- [ ] Add `McpSyncRequestContext` parameter to tool method
- [ ] Verify stateful server mode is enabled (required for elicitation)
- [ ] Create `LocationResolver` component
- [ ] Create `ResolvedStop` record
- [ ] Update `TripPlannerTool` to accept names instead of refs
- [ ] Update tests

## Open Questions

1. **Does `MethodToolCallbackProvider` support `@McpTool`?**
   - If not, we may need annotation-based component scanning or `McpToolCallbackProvider`.
   - Investigate before implementation.

2. **Is the server running in stateful mode?**
   - `spring-ai-starter-mcp-server-webflux` uses SSE transport which is stateful by default.
   - Elicitation should be available, but verify with a smoke test.

3. **Should `findLocation` tool remain?**
   - Recommendation: Keep it. It's useful for the LLM to explore locations independently (e.g. "what stops are near the airport?"). It just won't be required as a prerequisite for `planTrip` anymore.

4. **Elicitation UX: number-based pick vs. name-based pick?**
   - Current design: user provides a number (1, 2, 3...). Simple but depends on client rendering.
   - Alternative: pass the candidate names and let the MCP client render a proper selection UI.
   - Recommendation: Start with number-based, iterate based on client behavior.
