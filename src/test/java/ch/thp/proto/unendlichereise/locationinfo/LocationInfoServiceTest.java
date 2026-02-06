package ch.thp.proto.unendlichereise.locationinfo;

import ch.thp.proto.unendlichereise.locationinfo.model.LocationRequest;
import ch.thp.proto.unendlichereise.locationinfo.model.LocationResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocationInfoServiceTest {

    private MockWebServer mockWebServer;
    private LocationInfoService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .build();

        service = new LocationInfoService(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void findLocations_returnsResults_whenApiReturnsValidResponse() throws InterruptedException {
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <OJPResponse>
                        <siri:ServiceDelivery>
                            <OJPLocationInformationDelivery>
                                <PlaceResult>
                                    <Place>
                                        <StopPlace>
                                            <StopPlaceRef>8507000</StopPlaceRef>
                                            <StopPlaceName><Text>Bern</Text></StopPlaceName>
                                        </StopPlace>
                                        <Name><Text>Bern</Text></Name>
                                        <GeoPosition>
                                            <siri:Longitude>7.439122</siri:Longitude>
                                            <siri:Latitude>46.948825</siri:Latitude>
                                        </GeoPosition>
                                    </Place>
                                    <Complete>true</Complete>
                                </PlaceResult>
                            </OJPLocationInformationDelivery>
                        </siri:ServiceDelivery>
                    </OJPResponse>
                </OJP>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        LocationRequest request = new LocationRequest("Bern", 5);
        List<LocationResult> results = service.findLocations(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Bern");
        assertThat(results.get(0).type()).isEqualTo("stop");
        assertThat(results.get(0).longitude()).isEqualTo(7.439122);
        assertThat(results.get(0).latitude()).isEqualTo(46.948825);
        assertThat(results.get(0).stopRef()).isEqualTo("8507000");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/ojp20");
        assertThat(recordedRequest.getBody().readUtf8()).contains("<Name>Bern</Name>");
    }

    @Test
    void findLocations_escapesXmlCharacters_inName() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("<OJP xmlns=\"http://www.vdv.de/ojp\"></OJP>")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        LocationRequest request = new LocationRequest("Test<>&\"'Name", 5);
        service.findLocations(request);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertThat(body).contains("Test&lt;&gt;&amp;&quot;&apos;Name");
        assertThat(body).doesNotContain("Test<>&\"'Name");
    }

    @Test
    void findLocations_returnsEmptyList_whenApiReturnsError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        LocationRequest request = new LocationRequest("Bern", 5);
        List<LocationResult> results = service.findLocations(request);

        assertThat(results).isEmpty();
    }

    @Test
    void findLocations_returnsEmptyList_whenResponseIsEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        LocationRequest request = new LocationRequest("Bern", 5);
        List<LocationResult> results = service.findLocations(request);

        assertThat(results).isEmpty();
    }

    @Test
    void findLocations_handlesMultipleResults() {
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <OJPResponse>
                        <siri:ServiceDelivery>
                            <OJPLocationInformationDelivery>
                                <PlaceResult>
                                    <Place>
                                        <StopPlace>
                                            <StopPlaceRef>8507000</StopPlaceRef>
                                        </StopPlace>
                                        <Name><Text>Bern</Text></Name>
                                        <GeoPosition>
                                            <siri:Longitude>7.43</siri:Longitude>
                                            <siri:Latitude>46.94</siri:Latitude>
                                        </GeoPosition>
                                    </Place>
                                </PlaceResult>
                                <PlaceResult>
                                    <Place>
                                        <Address>
                                            <PublicCode>addr1</PublicCode>
                                        </Address>
                                        <Name><Text>Bern, Bahnhofplatz</Text></Name>
                                        <GeoPosition>
                                            <siri:Longitude>7.44</siri:Longitude>
                                            <siri:Latitude>46.95</siri:Latitude>
                                        </GeoPosition>
                                    </Place>
                                </PlaceResult>
                            </OJPLocationInformationDelivery>
                        </siri:ServiceDelivery>
                    </OJPResponse>
                </OJP>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        LocationRequest request = new LocationRequest("Bern", 5);
        List<LocationResult> results = service.findLocations(request);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).type()).isEqualTo("stop");
        assertThat(results.get(1).type()).isEqualTo("address");
    }
}
