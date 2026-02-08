package ch.thp.proto.unendlichereise.locationinfo;

import ch.thp.proto.unendlichereise.locationinfo.model.LocationRequest;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationResult;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationInfoTool {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 10;

    private final LocationInfoService locationInfoService;
    private final InputSanitizer inputSanitizer;

    @McpTool(description = "Find Swiss public transport stops, addresses, or POIs by name. Returns matching locations with coordinates.")
    public List<LocationResult> findLocation(
            @McpToolParam(description = "Location name to search") String name,
            @McpToolParam(description = "Maximum results (1-10, default 5)") Integer limit
    ) {
        String sanitizedName = inputSanitizer.sanitize(name);
        if (sanitizedName == null || sanitizedName.isBlank()) {
            log.warn("Empty or invalid location name provided");
            return List.of();
        }

        sanitizedName = inputSanitizer.truncate(sanitizedName, MAX_NAME_LENGTH);
        int effectiveLimit = clampLimit(limit);

        LocationRequest request = new LocationRequest(sanitizedName, effectiveLimit);
        return locationInfoService.findLocations(request);
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
    }
}
