package ch.thp.proto.unendlichereise.locationinfo.model;

public record LocationResult(
        String name,
        String type,
        Double longitude,
        Double latitude,
        String stopRef
) {
}
