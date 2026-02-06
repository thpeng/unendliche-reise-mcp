package ch.thp.proto.unendlichereise.locationinfo.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(10) Integer limit
) {
    public LocationRequest {
        if (limit == null) {
            limit = 5;
        }
    }
}
