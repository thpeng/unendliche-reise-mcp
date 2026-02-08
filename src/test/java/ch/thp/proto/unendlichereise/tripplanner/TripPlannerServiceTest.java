package ch.thp.proto.unendlichereise.tripplanner;

import ch.thp.proto.unendlichereise.tripplanner.model.TripRequest;
import ch.thp.proto.unendlichereise.tripplanner.model.TripResult;
import ch.thp.proto.unendlichereise.shared.ojp.OjpClient;
import ch.thp.proto.unendlichereise.shared.sanitizer.InputSanitizer;
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

class TripPlannerServiceTest {

    private MockWebServer mockWebServer;
    private TripPlannerService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .build();

        OjpClient ojpClient = new OjpClient(webClient);
        InputSanitizer inputSanitizer = new InputSanitizer();
        service = new TripPlannerService(ojpClient, inputSanitizer);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void planTrips_returnsSingleTrip_withTwoTimedLegs() throws InterruptedException {
        String response = buildTripResponse("""
                <TripResult xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <Trip>
                        <TripId>trip1</TripId>
                        <StartTime>2025-06-15T08:33:00+02:00</StartTime>
                        <EndTime>2025-06-15T09:21:00+02:00</EndTime>
                        <Duration>PT48M</Duration>
                        <Transfers>1</Transfers>
                        <Leg>
                            <TimedLeg>
                                <LegBoard>
                                    <StopPointName><Text>Bern</Text></StopPointName>
                                    <ServiceDeparture>
                                        <TimetabledTime>2025-06-15T08:33:00+02:00</TimetabledTime>
                                    </ServiceDeparture>
                                    <PlannedQuay><Text>3</Text></PlannedQuay>
                                </LegBoard>
                                <LegAlight>
                                    <StopPointName><Text>Olten</Text></StopPointName>
                                    <ServiceArrival>
                                        <TimetabledTime>2025-06-15T09:00:00+02:00</TimetabledTime>
                                    </ServiceArrival>
                                    <PlannedQuay><Text>7</Text></PlannedQuay>
                                </LegAlight>
                                <Service>
                                    <Mode><PtMode>rail</PtMode></Mode>
                                    <PublishedServiceName><Text>IC 1</Text></PublishedServiceName>
                                </Service>
                            </TimedLeg>
                        </Leg>
                        <Leg>
                            <TimedLeg>
                                <LegBoard>
                                    <StopPointName><Text>Olten</Text></StopPointName>
                                    <ServiceDeparture>
                                        <TimetabledTime>2025-06-15T09:05:00+02:00</TimetabledTime>
                                    </ServiceDeparture>
                                    <PlannedQuay><Text>12</Text></PlannedQuay>
                                </LegBoard>
                                <LegAlight>
                                    <StopPointName><Text>Zürich HB</Text></StopPointName>
                                    <ServiceArrival>
                                        <TimetabledTime>2025-06-15T09:21:00+02:00</TimetabledTime>
                                    </ServiceArrival>
                                    <PlannedQuay><Text>5</Text></PlannedQuay>
                                </LegAlight>
                                <Service>
                                    <Mode><PtMode>rail</PtMode></Mode>
                                    <PublishedServiceName><Text>S3</Text></PublishedServiceName>
                                </Service>
                            </TimedLeg>
                        </Leg>
                    </Trip>
                </TripResult>
                """);

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", "2025-06-15T08:30:00+02:00", 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).hasSize(1);
        TripResult trip = results.get(0);
        assertThat(trip.startTime()).isEqualTo("2025-06-15T08:33:00+02:00");
        assertThat(trip.endTime()).isEqualTo("2025-06-15T09:21:00+02:00");
        assertThat(trip.durationMinutes()).isEqualTo(48);
        assertThat(trip.transfers()).isEqualTo(1);
        assertThat(trip.legs()).hasSize(2);

        assertThat(trip.legs().get(0).mode()).isEqualTo("rail");
        assertThat(trip.legs().get(0).serviceName()).isEqualTo("IC 1");
        assertThat(trip.legs().get(0).fromName()).isEqualTo("Bern");
        assertThat(trip.legs().get(0).toName()).isEqualTo("Olten");
        assertThat(trip.legs().get(0).departure()).isEqualTo("2025-06-15T08:33:00+02:00");
        assertThat(trip.legs().get(0).arrival()).isEqualTo("2025-06-15T09:00:00+02:00");
        assertThat(trip.legs().get(0).departurePlatform()).isEqualTo("3");
        assertThat(trip.legs().get(0).arrivalPlatform()).isEqualTo("7");

        assertThat(trip.legs().get(1).serviceName()).isEqualTo("S3");
        assertThat(trip.legs().get(1).fromName()).isEqualTo("Olten");
        assertThat(trip.legs().get(1).toName()).isEqualTo("Zürich HB");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/ojp20");
    }

    @Test
    void planTrips_returnsMultipleTrips() {
        String response = buildTripResponse("""
                <TripResult xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <Trip>
                        <TripId>trip1</TripId>
                        <StartTime>2025-06-15T08:33:00+02:00</StartTime>
                        <EndTime>2025-06-15T09:21:00+02:00</EndTime>
                        <Duration>PT48M</Duration>
                        <Transfers>0</Transfers>
                        <Leg>
                            <TimedLeg>
                                <LegBoard>
                                    <StopPointName><Text>Bern</Text></StopPointName>
                                    <ServiceDeparture><TimetabledTime>2025-06-15T08:33:00+02:00</TimetabledTime></ServiceDeparture>
                                </LegBoard>
                                <LegAlight>
                                    <StopPointName><Text>Zürich HB</Text></StopPointName>
                                    <ServiceArrival><TimetabledTime>2025-06-15T09:21:00+02:00</TimetabledTime></ServiceArrival>
                                </LegAlight>
                                <Service>
                                    <Mode><PtMode>rail</PtMode></Mode>
                                    <PublishedServiceName><Text>IC 1</Text></PublishedServiceName>
                                </Service>
                            </TimedLeg>
                        </Leg>
                    </Trip>
                </TripResult>
                <TripResult xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <Trip>
                        <TripId>trip2</TripId>
                        <StartTime>2025-06-15T09:03:00+02:00</StartTime>
                        <EndTime>2025-06-15T09:56:00+02:00</EndTime>
                        <Duration>PT53M</Duration>
                        <Transfers>0</Transfers>
                        <Leg>
                            <TimedLeg>
                                <LegBoard>
                                    <StopPointName><Text>Bern</Text></StopPointName>
                                    <ServiceDeparture><TimetabledTime>2025-06-15T09:03:00+02:00</TimetabledTime></ServiceDeparture>
                                </LegBoard>
                                <LegAlight>
                                    <StopPointName><Text>Zürich HB</Text></StopPointName>
                                    <ServiceArrival><TimetabledTime>2025-06-15T09:56:00+02:00</TimetabledTime></ServiceArrival>
                                </LegAlight>
                                <Service>
                                    <Mode><PtMode>rail</PtMode></Mode>
                                    <PublishedServiceName><Text>IC 5</Text></PublishedServiceName>
                                </Service>
                            </TimedLeg>
                        </Leg>
                    </Trip>
                </TripResult>
                """);

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).durationMinutes()).isEqualTo(48);
        assertThat(results.get(1).durationMinutes()).isEqualTo(53);
    }

    @Test
    void planTrips_parsesTransferLeg_asModeWalk() {
        String response = buildTripResponse("""
                <TripResult xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <Trip>
                        <TripId>trip1</TripId>
                        <StartTime>2025-06-15T08:33:00+02:00</StartTime>
                        <EndTime>2025-06-15T09:21:00+02:00</EndTime>
                        <Duration>PT48M</Duration>
                        <Transfers>1</Transfers>
                        <Leg>
                            <TransferLeg>
                                <LegStart>
                                    <StopPointName><Text>Bern, Bahnhof</Text></StopPointName>
                                </LegStart>
                                <LegEnd>
                                    <StopPointName><Text>Bern</Text></StopPointName>
                                </LegEnd>
                                <TimeWindowStart>2025-06-15T08:20:00+02:00</TimeWindowStart>
                                <TimeWindowEnd>2025-06-15T08:28:00+02:00</TimeWindowEnd>
                            </TransferLeg>
                        </Leg>
                    </Trip>
                </TripResult>
                """);

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).legs()).hasSize(1);
        assertThat(results.get(0).legs().get(0).mode()).isEqualTo("walk");
        assertThat(results.get(0).legs().get(0).serviceName()).isNull();
        assertThat(results.get(0).legs().get(0).fromName()).isEqualTo("Bern, Bahnhof");
        assertThat(results.get(0).legs().get(0).toName()).isEqualTo("Bern");
        assertThat(results.get(0).legs().get(0).departure()).isEqualTo("2025-06-15T08:20:00+02:00");
        assertThat(results.get(0).legs().get(0).arrival()).isEqualTo("2025-06-15T08:28:00+02:00");
    }

    @Test
    void planTrips_parsesDuration_PT48M_to48Minutes() {
        String response = buildTripResponse("""
                <TripResult xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <Trip>
                        <TripId>trip1</TripId>
                        <StartTime>2025-06-15T08:33:00+02:00</StartTime>
                        <EndTime>2025-06-15T09:21:00+02:00</EndTime>
                        <Duration>PT48M</Duration>
                        <Transfers>0</Transfers>
                    </Trip>
                </TripResult>
                """);

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).durationMinutes()).isEqualTo(48);
    }

    @Test
    void planTrips_handlesMissingPlatform() {
        String response = buildTripResponse("""
                <TripResult xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <Trip>
                        <TripId>trip1</TripId>
                        <StartTime>2025-06-15T08:33:00+02:00</StartTime>
                        <EndTime>2025-06-15T09:21:00+02:00</EndTime>
                        <Duration>PT48M</Duration>
                        <Transfers>0</Transfers>
                        <Leg>
                            <TimedLeg>
                                <LegBoard>
                                    <StopPointName><Text>Bern</Text></StopPointName>
                                    <ServiceDeparture><TimetabledTime>2025-06-15T08:33:00+02:00</TimetabledTime></ServiceDeparture>
                                </LegBoard>
                                <LegAlight>
                                    <StopPointName><Text>Zürich HB</Text></StopPointName>
                                    <ServiceArrival><TimetabledTime>2025-06-15T09:21:00+02:00</TimetabledTime></ServiceArrival>
                                </LegAlight>
                                <Service>
                                    <Mode><PtMode>rail</PtMode></Mode>
                                    <PublishedServiceName><Text>IC 1</Text></PublishedServiceName>
                                </Service>
                            </TimedLeg>
                        </Leg>
                    </Trip>
                </TripResult>
                """);

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).legs().get(0).departurePlatform()).isNull();
        assertThat(results.get(0).legs().get(0).arrivalPlatform()).isNull();
    }

    @Test
    void planTrips_omitsDepArrTime_whenDepartureTimeIsNull() throws InterruptedException {
        String response = buildTripResponse("");

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        service.planTrips(request);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertThat(body).doesNotContain("<DepArrTime>");
    }

    @Test
    void planTrips_includesDepArrTime_whenDepartureTimeProvided() throws InterruptedException {
        String response = buildTripResponse("");

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", "2025-06-15T08:30:00+02:00", 3);
        service.planTrips(request);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertThat(body).contains("<DepArrTime>2025-06-15T08:30:00+02:00</DepArrTime>");
    }

    @Test
    void planTrips_returnsEmptyList_whenApiReturns500() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).isEmpty();
    }

    @Test
    void planTrips_returnsEmptyList_whenResponseIsEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        List<TripResult> results = service.planTrips(request);

        assertThat(results).isEmpty();
    }

    @Test
    void planTrips_requestContainsCorrectStopRefs() throws InterruptedException {
        String response = buildTripResponse("");

        mockWebServer.enqueue(new MockResponse()
                .setBody(response)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE));

        TripRequest request = new TripRequest("8507000", "8503000", null, 3);
        service.planTrips(request);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertThat(body).contains("<siri:StopPointRef>8507000</siri:StopPointRef>");
        assertThat(body).contains("<siri:StopPointRef>8503000</siri:StopPointRef>");
        assertThat(body).contains("<NumberOfResults>3</NumberOfResults>");
    }

    private String buildTripResponse(String tripResultsXml) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OJP xmlns="http://www.vdv.de/ojp" xmlns:siri="http://www.siri.org.uk/siri">
                    <OJPResponse>
                        <siri:ServiceDelivery>
                            <OJPTripDelivery>
                                %s
                            </OJPTripDelivery>
                        </siri:ServiceDelivery>
                    </OJPResponse>
                </OJP>
                """.formatted(tripResultsXml);
    }
}
