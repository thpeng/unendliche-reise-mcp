package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.tripplanner.model.TripLeg;
import ch.thp.proto.unendlichereise.tripplanner.model.TripResult;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripPlannerToolTest {

    @Mock
    private TripPlannerService tripPlannerService;

    private InputSanitizer inputSanitizer;
    private TripPlannerTool tool;

    @BeforeEach
    void setUp() {
        inputSanitizer = new InputSanitizer();
        tool = new TripPlannerTool(tripPlannerService, inputSanitizer);
    }

    @Test
    void planTrip_callsService_withValidInput() {
        TripResult result = new TripResult(
                "2025-06-15T08:33:00+02:00", "2025-06-15T09:21:00+02:00",
                48, 1, List.of(new TripLeg("rail", "IC 1", "Bern", "Zürich HB",
                        "2025-06-15T08:33:00+02:00", "2025-06-15T09:21:00+02:00", "3", "5")));
        when(tripPlannerService.planTrips(any())).thenReturn(List.of(result));

        List<TripResult> results = tool.planTrip("8507000", "8503000", "2025-06-15T08:30:00+02:00", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).durationMinutes()).isEqualTo(48);
        verify(tripPlannerService).planTrips(any());
    }

    @Test
    void planTrip_returnsEmptyList_whenOriginRefIsNull() {
        List<TripResult> results = tool.planTrip(null, "8503000", null, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsEmptyList_whenOriginRefIsBlank() {
        List<TripResult> results = tool.planTrip("   ", "8503000", null, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsEmptyList_whenDestinationRefIsNull() {
        List<TripResult> results = tool.planTrip("8507000", null, null, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsEmptyList_whenDestinationRefIsBlank() {
        List<TripResult> results = tool.planTrip("8507000", "  ", null, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_returnsEmptyList_whenOriginRefContainsInvalidChars() {
        List<TripResult> results = tool.planTrip("8507000; DROP", "8503000", null, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_acceptsNumericStopRef() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("8507000", "8503000", null, null);

        verify(tripPlannerService).planTrips(any());
    }

    @Test
    void planTrip_acceptsSloidStopRef() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("ch:1:sloid:3000", "ch:1:sloid:4000", null, null);

        verify(tripPlannerService).planTrips(any());
    }

    @Test
    void planTrip_acceptsNullDepartureTime() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("8507000", "8503000", null, null);

        verify(tripPlannerService).planTrips(argThat(req -> req.departureTime() == null));
    }

    @Test
    void planTrip_acceptsValidDepartureTime() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("8507000", "8503000", "2025-06-15T08:30:00+02:00", null);

        verify(tripPlannerService).planTrips(argThat(req ->
                req.departureTime().equals("2025-06-15T08:30:00+02:00")));
    }

    @Test
    void planTrip_returnsEmptyList_whenDepartureTimeIsInvalid() {
        List<TripResult> results = tool.planTrip("8507000", "8503000", "not-a-date", null);

        assertThat(results).isEmpty();
        verifyNoInteractions(tripPlannerService);
    }

    @Test
    void planTrip_defaultsLimitTo3_whenNull() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("8507000", "8503000", null, null);

        verify(tripPlannerService).planTrips(argThat(req -> req.limit() == 3));
    }

    @Test
    void planTrip_clampsLimitTo1_whenZero() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("8507000", "8503000", null, 0);

        verify(tripPlannerService).planTrips(argThat(req -> req.limit() == 1));
    }

    @Test
    void planTrip_clampsLimitTo5_whenTooHigh() {
        when(tripPlannerService.planTrips(any())).thenReturn(List.of());

        tool.planTrip("8507000", "8503000", null, 99);

        verify(tripPlannerService).planTrips(argThat(req -> req.limit() == 5));
    }
}
