package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.locationinfo.LocationInfoService;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationRequest;
import ch.thp.proto.unendlichereise.tripplanner.model.ResolvedStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationResolver {

    private static final int RESOLUTION_LIMIT = 5;

    private final LocationInfoService locationInfoService;

    public List<ResolvedStop> resolve(String name) {
        LocationRequest request = new LocationRequest(name, RESOLUTION_LIMIT);
        return locationInfoService.findLocations(request).stream()
                .filter(r -> r.stopRef() != null)
                .map(r -> new ResolvedStop(r.name(), r.stopRef()))
                .toList();
    }
}
