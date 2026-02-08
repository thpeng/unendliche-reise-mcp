package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.tripplanner.model.TripLeg;
import ch.thp.proto.unendlichereise.tripplanner.model.TripRequest;
import ch.thp.proto.unendlichereise.tripplanner.model.TripResult;
import ch.thp.proto.unendlichereise.shared.ojp.OjpClient;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripPlannerService {

    private final OjpClient ojpClient;
    private final InputSanitizer inputSanitizer;

    public List<TripResult> planTrips(TripRequest request) {
        String xmlRequest = buildRequest(request);

        return ojpClient.sendRequest(xmlRequest)
                .map(this::parseResponse)
                .orElse(List.of());
    }

    private String buildRequest(TripRequest request) {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String messageId = "TR-" + UUID.randomUUID().toString().substring(0, 8);
        String escapedOrigin = inputSanitizer.escapeXml(request.originRef());
        String escapedDestination = inputSanitizer.escapeXml(request.destinationRef());

        String depArrTimeBlock = "";
        if (request.departureTime() != null) {
            String escapedTime = inputSanitizer.escapeXml(request.departureTime());
            depArrTimeBlock = "<DepArrTime>" + escapedTime + "</DepArrTime>";
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
                  <OJPRequest>
                    <siri:ServiceRequest>
                      <siri:RequestTimestamp>%s</siri:RequestTimestamp>
                      <siri:RequestorRef>unendliche-reise-mcp</siri:RequestorRef>
                      <OJPTripRequest>
                        <siri:RequestTimestamp>%s</siri:RequestTimestamp>
                        <siri:MessageIdentifier>%s</siri:MessageIdentifier>
                        <Origin>
                          <PlaceRef>
                            <siri:StopPointRef>%s</siri:StopPointRef>
                          </PlaceRef>
                          %s
                        </Origin>
                        <Destination>
                          <PlaceRef>
                            <siri:StopPointRef>%s</siri:StopPointRef>
                          </PlaceRef>
                        </Destination>
                        <Params>
                          <NumberOfResults>%d</NumberOfResults>
                        </Params>
                      </OJPTripRequest>
                    </siri:ServiceRequest>
                  </OJPRequest>
                </OJP>
                """.formatted(timestamp, timestamp, messageId, escapedOrigin,
                depArrTimeBlock, escapedDestination, request.limit());
    }

    private List<TripResult> parseResponse(Document doc) {
        List<TripResult> results = new ArrayList<>();
        NodeList tripResults = ojpClient.getElementsByName(doc, "TripResult");

        for (int i = 0; i < tripResults.getLength(); i++) {
            Element tripResultElement = (Element) tripResults.item(i);
            TripResult result = extractTripResult(tripResultElement);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    private TripResult extractTripResult(Element tripResultElement) {
        try {
            Element trip = ojpClient.getFirstChildElement(tripResultElement, "Trip");
            if (trip == null) return null;

            String startTime = getDirectElementText(trip, "StartTime");
            String endTime = getDirectElementText(trip, "EndTime");
            int durationMinutes = parseDuration(getDirectElementText(trip, "Duration"));
            int transfers = parseTransfers(getDirectElementText(trip, "Transfers"));
            List<TripLeg> legs = extractLegs(trip);

            return new TripResult(startTime, endTime, durationMinutes, transfers, legs);
        } catch (Exception e) {
            log.warn("Error extracting trip result", e);
            return null;
        }
    }

    private List<TripLeg> extractLegs(Element trip) {
        List<TripLeg> legs = new ArrayList<>();
        NodeList legNodes = trip.getElementsByTagNameNS("http://www.vdv.de/ojp", "Leg");

        for (int i = 0; i < legNodes.getLength(); i++) {
            Element legElement = (Element) legNodes.item(i);
            // Check direct children only to avoid nested Leg elements
            if (!legElement.getParentNode().equals(trip)) continue;

            TripLeg leg = extractLeg(legElement);
            if (leg != null) {
                legs.add(leg);
            }
        }

        return legs;
    }

    private TripLeg extractLeg(Element legElement) {
        Element timedLeg = ojpClient.getFirstChildElement(legElement, "TimedLeg");
        if (timedLeg != null) {
            return extractTimedLeg(timedLeg);
        }

        Element transferLeg = ojpClient.getFirstChildElement(legElement, "TransferLeg");
        if (transferLeg != null) {
            return extractTransferLeg(transferLeg);
        }

        return null;
    }

    private TripLeg extractTimedLeg(Element timedLeg) {
        Element service = ojpClient.getFirstChildElement(timedLeg, "Service");
        String mode = null;
        String serviceName = null;
        if (service != null) {
            Element modeElement = ojpClient.getFirstChildElement(service, "Mode");
            if (modeElement != null) {
                mode = getDirectElementText(modeElement, "PtMode");
            }
            serviceName = ojpClient.getElementText(service, "PublishedServiceName");
        }

        Element legBoard = ojpClient.getFirstChildElement(timedLeg, "LegBoard");
        String fromName = null;
        String departure = null;
        String departurePlatform = null;
        if (legBoard != null) {
            fromName = ojpClient.getElementText(legBoard, "StopPointName");
            Element serviceDeparture = ojpClient.getFirstChildElement(legBoard, "ServiceDeparture");
            if (serviceDeparture != null) {
                departure = getDirectElementText(serviceDeparture, "TimetabledTime");
            }
            departurePlatform = ojpClient.getElementText(legBoard, "PlannedQuay");
        }

        Element legAlight = ojpClient.getFirstChildElement(timedLeg, "LegAlight");
        String toName = null;
        String arrival = null;
        String arrivalPlatform = null;
        if (legAlight != null) {
            toName = ojpClient.getElementText(legAlight, "StopPointName");
            Element serviceArrival = ojpClient.getFirstChildElement(legAlight, "ServiceArrival");
            if (serviceArrival != null) {
                arrival = getDirectElementText(serviceArrival, "TimetabledTime");
            }
            arrivalPlatform = ojpClient.getElementText(legAlight, "PlannedQuay");
        }

        return new TripLeg(mode, serviceName, fromName, toName, departure, arrival, departurePlatform, arrivalPlatform);
    }

    private TripLeg extractTransferLeg(Element transferLeg) {
        Element legStart = ojpClient.getFirstChildElement(transferLeg, "LegStart");
        String fromName = legStart != null ? ojpClient.getElementText(legStart, "StopPointName") : null;

        Element legEnd = ojpClient.getFirstChildElement(transferLeg, "LegEnd");
        String toName = legEnd != null ? ojpClient.getElementText(legEnd, "StopPointName") : null;

        String departure = getDirectElementText(transferLeg, "TimeWindowStart");
        String arrival = getDirectElementText(transferLeg, "TimeWindowEnd");

        return new TripLeg("walk", null, fromName, toName, departure, arrival, null, null);
    }

    private String getDirectElementText(Element parent, String localName) {
        Element child = ojpClient.getFirstChildElement(parent, localName);
        return child != null ? child.getTextContent().trim() : null;
    }

    private int parseDuration(String duration) {
        if (duration == null || duration.isBlank()) return 0;
        try {
            return (int) Duration.parse(duration).toMinutes();
        } catch (Exception e) {
            log.warn("Error parsing duration: {}", duration);
            return 0;
        }
    }

    private int parseTransfers(String transfers) {
        if (transfers == null || transfers.isBlank()) return 0;
        try {
            return Integer.parseInt(transfers);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
