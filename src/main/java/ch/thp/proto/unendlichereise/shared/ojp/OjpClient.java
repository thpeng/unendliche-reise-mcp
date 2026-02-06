package ch.thp.proto.unendlichereise.shared.ojp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OjpClient {

    private static final String OJP_NAMESPACE = "http://www.vdv.de/ojp";
    private static final String SIRI_NAMESPACE = "http://www.siri.org.uk/siri";

    private final WebClient ojpWebClient;

    public Optional<Document> sendRequest(String xmlRequest) {
        try {
            String response = ojpWebClient.post()
                    .uri("/ojp20")
                    .bodyValue(xmlRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseXml(response);
        } catch (Exception e) {
            log.error("Error calling OJP API", e);
            return Optional.empty();
        }
    }

    private Optional<Document> parseXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return Optional.of(builder.parse(new InputSource(new StringReader(xml))));
        } catch (Exception e) {
            log.error("Error parsing OJP response", e);
            return Optional.empty();
        }
    }

    public NodeList getElementsByName(Document doc, String localName) {
        return doc.getElementsByTagNameNS(OJP_NAMESPACE, localName);
    }

    public Element getFirstChildElement(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS(OJP_NAMESPACE, localName);
        return children.getLength() > 0 ? (Element) children.item(0) : null;
    }

    public boolean hasChildElement(Element parent, String localName) {
        return getFirstChildElement(parent, localName) != null;
    }

    public String getElementText(Element parent, String localName) {
        Element child = getFirstChildElement(parent, localName);
        if (child != null) {
            Element textElement = getFirstChildElement(child, "Text");
            if (textElement != null) {
                return textElement.getTextContent().trim();
            }
            return child.getTextContent().trim();
        }
        return null;
    }

    public String getSiriElementText(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS(SIRI_NAMESPACE, localName);
        return children.getLength() > 0 ? children.item(0).getTextContent().trim() : null;
    }

    public String getNestedSiriText(Element parent, String parentLocalName, String childLocalName) {
        Element nested = getFirstChildElement(parent, parentLocalName);
        return nested != null ? getSiriElementText(nested, childLocalName) : null;
    }
}
