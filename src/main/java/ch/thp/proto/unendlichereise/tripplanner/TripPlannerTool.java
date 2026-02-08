package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.tripplanner.model.TripRequest;
import ch.thp.proto.unendlichereise.tripplanner.model.TripResult;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripPlannerTool {

    private static final Pattern STOP_REF_PATTERN = Pattern.compile("^[a-zA-Z0-9:]+$");
    private static final Pattern DEPARTURE_TIME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}$");
    private static final int DEFAULT_LIMIT = 3;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 5;

    private final TripPlannerService tripPlannerService;
    private final InputSanitizer inputSanitizer;

    @Tool(description = "Plan a journey between two Swiss public transport stops. Returns trip options with legs, transfers, and timing.")
    public List<TripResult> planTrip(
            @ToolParam(description = "Origin stop ref from findLocation") String originRef,
            @ToolParam(description = "Destination stop ref from findLocation") String destinationRef,
            @ToolParam(description = "ISO-8601 datetime, e.g. 2025-06-15T08:30:00+02:00. Default: now") String departureTime,
            @ToolParam(description = "Maximum results (1-5, default 3)") Integer limit
    ) {
        if (!isValidStopRef(originRef) || !isValidStopRef(destinationRef)) {
            log.warn("Invalid stop ref: origin={}, destination={}", originRef, destinationRef);
            return List.of();
        }

        if (departureTime != null && !DEPARTURE_TIME_PATTERN.matcher(departureTime).matches()) {
            log.warn("Invalid departure time format: {}", departureTime);
            return List.of();
        }

        String sanitizedOrigin = inputSanitizer.sanitize(originRef);
        String sanitizedDestination = inputSanitizer.sanitize(destinationRef);
        String sanitizedDepartureTime = departureTime != null ? inputSanitizer.sanitize(departureTime) : null;
        int effectiveLimit = clampLimit(limit);

        TripRequest request = new TripRequest(sanitizedOrigin, sanitizedDestination, sanitizedDepartureTime, effectiveLimit);
        return tripPlannerService.planTrips(request);
    }

    private boolean isValidStopRef(String stopRef) {
        if (stopRef == null || stopRef.isBlank()) return false;
        return STOP_REF_PATTERN.matcher(stopRef.trim()).matches();
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
    }
}
