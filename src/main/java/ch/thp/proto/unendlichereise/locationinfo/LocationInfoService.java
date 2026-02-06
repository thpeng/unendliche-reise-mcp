package ch.thp.proto.unendlichereise.locationinfo;

import ch.thp.proto.unendlichereise.locationinfo.model.LocationRequest;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationResult;
import ch.thp.proto.unendlichereise.shared.ojp.OjpClient;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationInfoService {

    private final OjpClient ojpClient;
    private final InputSanitizer inputSanitizer;

    public List<LocationResult> findLocations(LocationRequest request) {
        String xmlRequest = buildRequest(request);

        return ojpClient.sendRequest(xmlRequest)
                .map(this::parseResponse)
                .orElse(List.of());
    }

    private String buildRequest(LocationRequest request) {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String messageId = "LIR-" + UUID.randomUUID().toString().substring(0, 8);
        String escapedName = inputSanitizer.escapeXml(request.name());

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri" version="2.0">
                  <OJPRequest>
                    <siri:ServiceRequest>
                      <siri:RequestTimestamp>%s</siri:RequestTimestamp>
                      <siri:RequestorRef>unendliche-reise-mcp</siri:RequestorRef>
                      <OJPLocationInformationRequest>
                        <siri:RequestTimestamp>%s</siri:RequestTimestamp>
                        <siri:MessageIdentifier>%s</siri:MessageIdentifier>
                        <InitialInput>
                          <Name>%s</Name>
                        </InitialInput>
                        <Restrictions>
                          <NumberOfResults>%d</NumberOfResults>
                        </Restrictions>
                      </OJPLocationInformationRequest>
                    </siri:ServiceRequest>
                  </OJPRequest>
                </OJP>
                """.formatted(timestamp, timestamp, messageId, escapedName, request.limit());
    }

    private List<LocationResult> parseResponse(Document doc) {
        List<LocationResult> results = new ArrayList<>();
        NodeList placeResults = ojpClient.getElementsByName(doc, "PlaceResult");

        for (int i = 0; i < placeResults.getLength(); i++) {
            Element placeResult = (Element) placeResults.item(i);
            LocationResult result = extractLocationResult(placeResult);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    private LocationResult extractLocationResult(Element placeResult) {
        try {
            Element place = ojpClient.getFirstChildElement(placeResult, "Place");
            if (place == null) return null;

            String name = ojpClient.getElementText(place, "Name");
            String type = determineType(place);
            Double longitude = parseDouble(ojpClient.getNestedSiriText(place, "GeoPosition", "Longitude"));
            Double latitude = parseDouble(ojpClient.getNestedSiriText(place, "GeoPosition", "Latitude"));
            String stopRef = extractStopRef(place);

            return new LocationResult(name, type, longitude, latitude, stopRef);
        } catch (Exception e) {
            log.warn("Error extracting location result", e);
            return null;
        }
    }

    private String determineType(Element place) {
        if (ojpClient.hasChildElement(place, "StopPlace")) return "stop";
        if (ojpClient.hasChildElement(place, "StopPoint")) return "stop";
        if (ojpClient.hasChildElement(place, "Address")) return "address";
        if (ojpClient.hasChildElement(place, "PointOfInterest")) return "poi";
        if (ojpClient.hasChildElement(place, "TopographicPlace")) return "topographicPlace";
        return "location";
    }

    private String extractStopRef(Element place) {
        Element stopPlace = ojpClient.getFirstChildElement(place, "StopPlace");
        if (stopPlace != null) {
            return ojpClient.getElementText(stopPlace, "StopPlaceRef");
        }
        Element stopPoint = ojpClient.getFirstChildElement(place, "StopPoint");
        if (stopPoint != null) {
            return ojpClient.getSiriElementText(stopPoint, "StopPointRef");
        }
        return null;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
