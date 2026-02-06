package ch.thp.proto.unendlichereise.locationinfo;

import ch.thp.proto.unendlichereise.locationinfo.model.LocationRequest;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationResult;
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
public class LocationInfoTool {

    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(
            "(?i)(ignore|forget|disregard|you are|act as|pretend|system prompt)",
            Pattern.CASE_INSENSITIVE
    );

    private final LocationInfoService locationInfoService;

    @Tool(description = "Find Swiss public transport stops, addresses, or POIs by name. Returns matching locations with coordinates.")
    public List<LocationResult> findLocation(
            @ToolParam(description = "Location name to search") String name,
            @ToolParam(description = "Maximum results (1-10, default 5)") Integer limit
    ) {
        String sanitizedName = sanitizeInput(name);
        if (sanitizedName == null || sanitizedName.isBlank()) {
            log.warn("Empty or invalid location name provided");
            return List.of();
        }

        if (sanitizedName.length() > 100) {
            sanitizedName = sanitizedName.substring(0, 100);
        }

        int effectiveLimit = (limit == null) ? 5 : Math.min(Math.max(limit, 1), 10);

        LocationRequest request = new LocationRequest(sanitizedName, effectiveLimit);
        return locationInfoService.findLocations(request);
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;

        // Remove control characters
        String sanitized = input.replaceAll("[\\p{Cntrl}]", "");

        // Log suspicious patterns
        if (SUSPICIOUS_PATTERN.matcher(sanitized).find()) {
            log.warn("Suspicious input pattern detected: {}", sanitized.substring(0, Math.min(50, sanitized.length())));
        }

        return sanitized.trim();
    }
}
