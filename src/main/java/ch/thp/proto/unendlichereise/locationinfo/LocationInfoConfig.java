package ch.thp.proto.unendlichereise.locationinfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LocationInfoConfig {

    @Value("${ojp.api.base-url:https://api.opentransportdata.swiss}")
    private String baseUrl;

    @Value("${ojp.api.token:${OJP_AUTH_TOKEN:}}")
    private String apiToken;

    @Bean
    public WebClient ojpWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .build();
    }
}
