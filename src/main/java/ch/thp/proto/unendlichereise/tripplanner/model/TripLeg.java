package ch.thp.proto.unendlichereise.tripplanner.model;

public record TripLeg(
        String mode,
        String serviceName,
        String fromName,
        String toName,
        String departure,
        String arrival,
        String departurePlatform,
        String arrivalPlatform
) {}
