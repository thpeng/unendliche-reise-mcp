package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.tripplanner.model.ResolvedStop;
import ch.thp.proto.unendlichereise.tripplanner.model.TripLeg;
import ch.thp.proto.unendlichereise.tripplanner.model.TripResult;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.context.McpSyncRequestContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripPlannerToolTest {

    @Mock
    private TripPlannerService tripPlannerService;

    @Mock
    private LocationResolver locationResolver;

    @Mock
    private McpSyncRequestContext context;

    private InputSanitizer inputSanitizer;
    private TripPlannerTool tool;

    @BeforeEach
    void setUp() {
        inputSanitizer = new InputSanitizer();
        tool = new TripPlannerTool(tripPlannerService, locationResolver, inputSanitizer);

        // Default: elicitation not enabled for most tests
        lenient().when(context.elicitEnabled()).thenReturn(false);
    }

    @Test
    void planTrip_callsService_whenBothStopsResolveTo1Candidate() {
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        when(locationResolver.resolve("Basel SBB")).thenReturn(List.of(
                new ResolvedStop("Basel SBB", "8500010")));

        TripResult trip = new TripResult(
                "2025-06-15T08:33:00+02:00", "2025-06-15T09:21:00+02:00",
                48, 1, List.of(new TripLeg("rail", "IC 1", "Bern", "Basel SBB",
                "2025-06-15T08:33:00+02:00", "2025-06-15T09:21:00+02:00", "3", "5")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of(trip));

        Object result = tool.planTrip("Bern", "Basel SBB", "2025-06-15T08:30:00+02:00", 3, context);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<TripResult> trips = (List<TripResult>) result;
        assertThat(trips).hasSize(1);
        assertThat(trips.get(0).durationMinutes()).isEqualTo(48);
        verify(tripPlannerService).planTrips(argThat(req ->
                req.originRef().equals("8507000") && req.destinationRef().equals("8500010")));
    }

    @Test
    void planTrip_returnsError_whenOriginIsNull() {
        Object result = tool.planTrip(null, "Basel SBB", null, null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error")).isEqualTo("Origin must not be empty.");
        verifyNoInteractions(tripPlannerService);
        verifyNoInteractions(locationResolver);
    }

    @Test
    void planTrip_returnsError_whenOriginIsBlank() {
        Object result = tool.planTrip("   ", "Basel SBB", null, null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error")).isEqualTo("Origin must not be empty.");
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsError_whenDestinationIsNull() {
        Object result = tool.planTrip("Bern", null, null, null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error")).isEqualTo("Destination must not be empty.");
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsError_whenDestinationIsBlank() {
        Object result = tool.planTrip("Bern", "  ", null, null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error")).isEqualTo("Destination must not be empty.");
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsError_whenDepartureTimeIsInvalid() {
        Object result = tool.planTrip("Bern", "Basel SBB", "not-a-date", null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error").toString()).contains("Invalid departure time");
        verifyNoInteractions(tripPlannerService);
        verifyNoInteractions(locationResolver);
    }

    @Test
    void planTrip_acceptsNullDepartureTime() {
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        when(locationResolver.resolve("Basel")).thenReturn(List.of(
                new ResolvedStop("Basel SBB", "8500010")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern", "Basel", null, null, context);

        verify(tripPlannerService).planTrips(argThat(req -> req.departureTime() == null));
    }

    @Test
    void planTrip_acceptsValidDepartureTime() {
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        when(locationResolver.resolve("Basel")).thenReturn(List.of(
                new ResolvedStop("Basel SBB", "8500010")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern", "Basel", "2025-06-15T08:30:00+02:00", null, context);

        verify(tripPlannerService).planTrips(argThat(req ->
                req.departureTime().equals("2025-06-15T08:30:00+02:00")));
    }

    @Test
    void planTrip_defaultsLimitTo3_whenNull() {
        when(locationResolver.resolve(any())).thenReturn(List.of(
                new ResolvedStop("X", "1")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern", "Basel", null, null, context);

        verify(tripPlannerService).planTrips(argThat(req -> req.limit() == 3));
    }

    @Test
    void planTrip_clampsLimitTo1_whenZero() {
        when(locationResolver.resolve(any())).thenReturn(List.of(
                new ResolvedStop("X", "1")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern", "Basel", null, 0, context);

        verify(tripPlannerService).planTrips(argThat(req -> req.limit() == 1));
    }

    @Test
    void planTrip_clampsLimitTo5_whenTooHigh() {
        when(locationResolver.resolve(any())).thenReturn(List.of(
                new ResolvedStop("X", "1")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern", "Basel", null, 99, context);

        verify(tripPlannerService).planTrips(argThat(req -> req.limit() == 5));
    }

    @Test
    void planTrip_returnsError_whenOriginCannotBeResolved() {
        when(locationResolver.resolve(anyString())).thenReturn(List.of());

        Object result = tool.planTrip("Nonexistent", "Basel SBB", null, null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error").toString()).contains("Could not resolve origin");
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsError_whenDestinationCannotBeResolved() {
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        when(locationResolver.resolve(argThat(s -> !s.equals("Bern")))).thenReturn(List.of());

        Object result = tool.planTrip("Bern", "Nonexistent", null, null, context);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("error").toString()).contains("Could not resolve destination");
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_usesFirstCandidate_whenOriginIsAmbiguous() {
        // InputSanitizer removes umlaut, so "Zürich" becomes "Zrich"
        when(locationResolver.resolve("Zrich")).thenReturn(List.of(
                new ResolvedStop("Zürich HB", "8503000"),
                new ResolvedStop("Zürich Flughafen", "8503016"),
                new ResolvedStop("Zürich Oerlikon", "8503006")));
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Zürich", "Bern", null, null, context);

        verify(tripPlannerService).planTrips(argThat(req ->
                req.originRef().equals("8503000")));
    }

    @Test
    void planTrip_usesFirstCandidate_whenDestinationIsAmbiguous() {
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        // InputSanitizer removes umlaut, so "Zürich" becomes "Zrich"
        when(locationResolver.resolve("Zrich")).thenReturn(List.of(
                new ResolvedStop("Zürich HB", "8503000"),
                new ResolvedStop("Zürich Flughafen", "8503016")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern", "Zürich", null, null, context);

        verify(tripPlannerService).planTrips(argThat(req ->
                req.destinationRef().equals("8503000")));
    }

    @Test
    void planTrip_sanitizesInput_removingControlCharacters() {
        when(locationResolver.resolve("Bern")).thenReturn(List.of(
                new ResolvedStop("Bern", "8507000")));
        when(locationResolver.resolve("Basel")).thenReturn(List.of(
                new ResolvedStop("Basel SBB", "8500010")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("Bern\n\t", "Basel\r", null, null, context);

        verify(locationResolver).resolve("Bern");
        verify(locationResolver).resolve("Basel");
    }

    @Test
    void planTrip_truncatesLongNames() {
        String longName = "A".repeat(150);
        String truncated = "A".repeat(100);
        when(locationResolver.resolve(truncated)).thenReturn(List.of(
                new ResolvedStop("X", "1")));
        when(locationResolver.resolve("Basel")).thenReturn(List.of(
                new ResolvedStop("Basel", "2")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip(longName, "Basel", null, null, context);

        verify(locationResolver).resolve(truncated);
    }
}