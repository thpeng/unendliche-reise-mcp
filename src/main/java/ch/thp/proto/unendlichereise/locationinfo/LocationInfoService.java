package ch.thp.proto.unendlichereise.locationinfo;

import ch.thp.proto.unendlichereise.locationinfo.model.LocationRequest;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationInfoService {

    private final WebClient ojpWebClient;

    public List<LocationResult> findLocations(LocationRequest request) {
        String xmlRequest = buildRequest(request);

        try {
            String response = ojpWebClient.post()
                    .uri("/ojp20")
                    .bodyValue(xmlRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);
        } catch (Exception e) {
            log.error("Error calling OJP API", e);
            return List.of();
        }
    }

    private String buildRequest(LocationRequest request) {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String messageId = "LIR-" + UUID.randomUUID().toString().substring(0, 8);
        String escapedName = escapeXml(request.name());

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

    private String escapeXml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private List<LocationResult> parseResponse(String xml) {
        List<LocationResult> results = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return results;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList placeResults = doc.getElementsByTagNameNS("http://www.vdv.de/ojp", "PlaceResult");

            for (int i = 0; i < placeResults.getLength(); i++) {
                Element placeResult = (Element) placeResults.item(i);
                LocationResult result = extractLocationResult(placeResult);
                if (result != null) {
                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing OJP response", e);
        }

        return results;
    }

    private LocationResult extractLocationResult(Element placeResult) {
        try {
            Element place = getFirstChildElement(placeResult, "Place");
            if (place == null) return null;

            String name = getElementText(place, "Name");
            String type = determineType(place);
            Double longitude = parseDouble(getNestedElementText(place, "GeoPosition", "Longitude"));
            Double latitude = parseDouble(getNestedElementText(place, "GeoPosition", "Latitude"));
            String stopRef = extractStopRef(place);

            return new LocationResult(name, type, longitude, latitude, stopRef);
        } catch (Exception e) {
            log.warn("Error extracting location result", e);
            return null;
        }
    }

    private String determineType(Element place) {
        if (hasChildElement(place, "StopPlace")) return "stop";
        if (hasChildElement(place, "StopPoint")) return "stop";
        if (hasChildElement(place, "Address")) return "address";
        if (hasChildElement(place, "PointOfInterest")) return "poi";
        if (hasChildElement(place, "TopographicPlace")) return "topographicPlace";
        return "location";
    }

    private String extractStopRef(Element place) {
        Element stopPlace = getFirstChildElement(place, "StopPlace");
        if (stopPlace != null) {
            return getElementText(stopPlace, "StopPlaceRef");
        }
        Element stopPoint = getFirstChildElement(place, "StopPoint");
        if (stopPoint != null) {
            return getElementTextNS(stopPoint, "http://www.siri.org.uk/siri", "StopPointRef");
        }
        return null;
    }

    private Element getFirstChildElement(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS("http://www.vdv.de/ojp", localName);
        if (children.getLength() > 0) {
            return (Element) children.item(0);
        }
        return null;
    }

    private boolean hasChildElement(Element parent, String localName) {
        return getFirstChildElement(parent, localName) != null;
    }

    private String getElementText(Element parent, String localName) {
        Element child = getFirstChildElement(parent, localName);
        if (child != null) {
            // Check for Text sub-element first
            Element textElement = getFirstChildElement(child, "Text");
            if (textElement != null) {
                return textElement.getTextContent().trim();
            }
            return child.getTextContent().trim();
        }
        return null;
    }

    private String getElementTextNS(Element parent, String namespace, String localName) {
        NodeList children = parent.getElementsByTagNameNS(namespace, localName);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent().trim();
        }
        return null;
    }

    private String getNestedElementText(Element parent, String parentLocalName, String childLocalName) {
        Element nested = getFirstChildElement(parent, parentLocalName);
        if (nested != null) {
            return getElementTextNS(nested, "http://www.siri.org.uk/siri", childLocalName);
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
