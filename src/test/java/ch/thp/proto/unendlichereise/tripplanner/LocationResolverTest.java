package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.locationinfo.LocationInfoService;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationResult;
import ch.thp.proto.unendlichereise.tripplanner.model.ResolvedStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationResolverTest {

    @Mock
    private LocationInfoService locationInfoService;

    private LocationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LocationResolver(locationInfoService);
    }

    @Test
    void resolve_returnsSingleCandidate_whenOneStopMatches() {
        when(locationInfoService.findLocations(any())).thenReturn(List.of(
                new LocationResult("Bern", "stop", 7.43, 46.94, "8507000")
        ));

        List<ResolvedStop> result = resolver.resolve("Bern");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Bern");
        assertThat(result.get(0).stopRef()).isEqualTo("8507000");
    }

    @Test
    void resolve_returnsMultipleCandidates_whenMultipleStopsMatch() {
        when(locationInfoService.findLocations(any())).thenReturn(List.of(
                new LocationResult("Zürich HB", "stop", 8.54, 47.37, "8503000"),
                new LocationResult("Zürich Flughafen", "stop", 8.56, 47.45, "8503016"),
                new LocationResult("Zürich Oerlikon", "stop", 8.54, 47.40, "8503006")
        ));

        List<ResolvedStop> result = resolver.resolve("Zürich");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).name()).isEqualTo("Zürich HB");
        assertThat(result.get(1).name()).isEqualTo("Zürich Flughafen");
        assertThat(result.get(2).name()).isEqualTo("Zürich Oerlikon");
    }

    @Test
    void resolve_returnsEmptyList_whenNoMatches() {
        when(locationInfoService.findLocations(any())).thenReturn(List.of());

        List<ResolvedStop> result = resolver.resolve("Nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_filtersOutNonStops_whenAddressesAndPoisReturned() {
        when(locationInfoService.findLocations(any())).thenReturn(List.of(
                new LocationResult("Bern", "stop", 7.43, 46.94, "8507000"),
                new LocationResult("Bern, Bahnhofplatz", "address", 7.44, 46.95, null),
                new LocationResult("Bern Tierpark", "poi", 7.45, 46.93, null)
        ));

        List<ResolvedStop> result = resolver.resolve("Bern");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Bern");
        assertThat(result.get(0).stopRef()).isEqualTo("8507000");
    }

    @Test
    void resolve_returnsEmptyList_whenOnlyAddressesMatch() {
        when(locationInfoService.findLocations(any())).thenReturn(List.of(
                new LocationResult("Bern, Bahnhofplatz", "address", 7.44, 46.95, null)
        ));

        List<ResolvedStop> result = resolver.resolve("Bahnhofplatz");

        assertThat(result).isEmpty();
    }
}
