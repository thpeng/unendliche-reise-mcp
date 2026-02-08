package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.tripplanner.model.ResolvedStop;
import ch.thp.proto.unendlichereise.tripplanner.model.TripRequest;
import ch.thp.proto.unendlichereise.tripplanner.model.TripResult;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripPlannerTool {

    private static final Pattern DEPARTURE_TIME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}$");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int DEFAULT_LIMIT = 3;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 5;

    private final TripPlannerService tripPlannerService;
    private final LocationResolver locationResolver;
    private final InputSanitizer inputSanitizer;

    @McpTool(description = "Plan a journey between two Swiss public transport stops by name. "
            + "Resolves locations automatically and returns trip options with legs, transfers, and timing.")
    public Object planTrip(
            @McpToolParam(description = "Origin stop name, e.g. 'Bern' or 'Zürich HB'") String origin,
            @McpToolParam(description = "Destination stop name, e.g. 'Basel SBB'") String destination,
            @McpToolParam(description = "ISO-8601 datetime, e.g. 2025-06-15T08:30:00+02:00. Default: now") String departureTime,
            @McpToolParam(description = "Maximum results (1-5, default 3)") Integer limit,
            McpSyncRequestContext context
    ) {
        String sanitizedOrigin = sanitizeName(origin);
        String sanitizedDestination = sanitizeName(destination);

        if (sanitizedOrigin == null || sanitizedOrigin.isBlank()) {
            return Map.of("error", "Origin must not be empty.");
        }
        if (sanitizedDestination == null || sanitizedDestination.isBlank()) {
            return Map.of("error", "Destination must not be empty.");
        }

        if (departureTime != null && !DEPARTURE_TIME_PATTERN.matcher(departureTime).matches()) {
            return Map.of("error", "Invalid departure time format. Expected: 2025-06-15T08:30:00+02:00");
        }

        int effectiveLimit = clampLimit(limit);

        ResolvedStop resolvedOrigin = resolveStop(sanitizedOrigin, context, "origin");
        if (resolvedOrigin == null) {
            return Map.of("error", "Could not resolve origin: " + sanitizedOrigin);
        }

        ResolvedStop resolvedDestination = resolveStop(sanitizedDestination, context, "destination");
        if (resolvedDestination == null) {
            return Map.of("error", "Could not resolve destination: " + sanitizedDestination);
        }

        String sanitizedDepartureTime = departureTime != null ? inputSanitizer.sanitize(departureTime) : null;
        TripRequest request = new TripRequest(
                resolvedOrigin.stopRef(), resolvedDestination.stopRef(),
                sanitizedDepartureTime, effectiveLimit);

        return tripPlannerService.planTrips(request);
    }

    private ResolvedStop resolveStop(String name, McpSyncRequestContext context, String locationType) {
        List<ResolvedStop> candidates = locationResolver.resolve(name);

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Ambiguous: try to elicit user choice if supported
        if (context.elicitEnabled()) {
            log.info("Ambiguous stop '{}' with {} candidates, eliciting user choice",
                    name, candidates.size());

            String options = IntStream.range(0, Math.min(candidates.size(), 10))
                    .mapToObj(i -> String.format("%d. %s", i + 1, candidates.get(i).name()))
                    .collect(Collectors.joining("\n"));

            String message = String.format(
                    "Multiple matches found for %s '%s'. Please select one:\n\n%s",
                    locationType, name, options);

            var elicitResult = context.elicit(
                    e -> e.message(message).meta("type", "location-disambiguation"),
                    String.class
            );

            if (elicitResult.action() == McpSchema.ElicitResult.Action.ACCEPT) {
                String userChoice = elicitResult.structuredContent();
                try {
                    int selectedIndex = Integer.parseInt(userChoice.trim()) - 1;
                    if (selectedIndex >= 0 && selectedIndex < candidates.size()) {
                        log.info("User selected option {} for {}: {}",
                                selectedIndex + 1, locationType, candidates.get(selectedIndex).name());
                        return candidates.get(selectedIndex);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid user selection: {}", userChoice);
                }
            }
        }

        // Fallback: use first (best-ranked) candidate
        log.info("Using first match for ambiguous stop '{}': {}", name, candidates.get(0).name());
        return candidates.get(0);
    }

    private String sanitizeName(String name) {
        if (name == null) return null;
        String sanitized = inputSanitizer.sanitize(name);
        return inputSanitizer.truncate(sanitized, 5);
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
    }
}