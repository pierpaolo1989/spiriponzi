package com.soa.spiriponzi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudflareAnalyticsServiceTest {
    private RestTemplate client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new RestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
    }

    @Test
    void aggregatesPageLoadsByCountryWithoutExposingTheApiToken() {
        server.expect(requestTo("https://api.cloudflare.com/client/v4/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-token"))
                .andRespond(withSuccess("""
                        {"data":{"viewer":{"accounts":[{"rumPageloadEventsAdaptiveGroups":[
                          {"count":8,"dimensions":{"countryName":"IT"}},
                          {"count":3,"dimensions":{"countryName":"FR"}},
                          {"count":4,"dimensions":{"countryName":"IT"}}
                        ]}]}},"errors":null}
                        """, MediaType.APPLICATION_JSON));

        var result = new CloudflareAnalyticsService(client, "test-api-token", "account-id", "site-tag")
                .getPageLoads();

        assertEquals(15, result.pageLoads());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.CountryPageLoads("IT", 12),
                new CloudflareAnalyticsService.CountryPageLoads("FR", 3)), result.countries());
        server.verify();
    }

    @Test
    void missingCredentialsAreReportedWithoutCallingCloudflare() {
        var service = new CloudflareAnalyticsService(client, "", "account-id", "site-tag");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, service::getPageLoads).getStatusCode());
        server.verify();
    }

    @Test
    void graphqlErrorsAreNotReportedAsEmptyAnalytics() {
        server.expect(requestTo("https://api.cloudflare.com/client/v4/graphql"))
                .andExpect(header("Authorization", "Bearer test-api-token"))
                .andRespond(withSuccess("""
                        {"data":null,"errors":[{"message":"permission denied"}]}
                        """, MediaType.APPLICATION_JSON));
        var service = new CloudflareAnalyticsService(client, "test-api-token", "account-id", "site-tag");

        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, service::getPageLoads).getStatusCode());
        server.verify();
    }

    @Test
    void queriesTheSupportedCountryNameDimension() {
        server.expect(requestTo("https://api.cloudflare.com/client/v4/graphql"))
                .andExpect(content().string(containsString("dimensions { countryName }")))
                .andRespond(withSuccess("""
                        {"data":{"viewer":{"accounts":[{"rumPageloadEventsAdaptiveGroups":[
                          {"count":6,"dimensions":{"countryName":"IT"}}
                        ]}]}},"errors":null}
                        """, MediaType.APPLICATION_JSON));

        var result = new CloudflareAnalyticsService(client, "test-api-token", "account-id", "site-tag")
                .getPageLoads();

        assertEquals(6, result.pageLoads());
        assertEquals(java.util.List.of(new CloudflareAnalyticsService.CountryPageLoads("IT", 6)), result.countries());
        server.verify();
    }
}
