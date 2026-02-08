package ch.thp.proto.unendlichereise.tripplanner.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TripRequest(
        @NotBlank String originRef,
        @NotBlank String destinationRef,
        String departureTime,
        @Min(1) @Max(5) Integer limit
) {
    public TripRequest {
        if (limit == null) {
            limit = 3;
        }
    }
}
