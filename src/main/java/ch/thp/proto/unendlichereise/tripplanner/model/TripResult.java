package ch.thp.proto.unendlichereise.tripplanner.model;

import java.util.List;

public record TripResult(
        String startTime,
        String endTime,
        int durationMinutes,
        int transfers,
        List<TripLeg> legs
) {}
