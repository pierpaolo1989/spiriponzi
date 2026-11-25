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
    void aggregatesPageLoadsAcrossAnalyticsDimensionsWithoutExposingTheApiToken() {
        server.expect(requestTo("https://api.cloudflare.com/client/v4/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-token"))
                .andExpect(content().string(containsString("dimensions { countryName }")))
                .andExpect(content().string(containsString("dimensions { requestPath }")))
                .andExpect(content().string(containsString("dimensions { userAgentBrowser }")))
                .andExpect(content().string(containsString("dimensions { deviceType }")))
                .andExpect(content().string(containsString("dimensions { refererHost }")))
                .andRespond(withSuccess("""
                        {"data":{"viewer":{"accounts":[{"daily":[
                          {"count":8,"dimensions":{"date":"2026-11-24"}},
                          {"count":7,"dimensions":{"date":"2026-11-25"}}
                        ],"countries":[
                          {"count":12,"dimensions":{"countryName":"IT"}},
                          {"count":3,"dimensions":{"countryName":"FR"}}
                        ],"topPages":[
                          {"count":9,"dimensions":{"requestPath":"/"}},
                          {"count":6,"dimensions":{"requestPath":"/survey"}}
                        ],"browsers":[
                          {"count":10,"dimensions":{"userAgentBrowser":"Chrome"}}
                        ],"devices":[
                          {"count":15,"dimensions":{"deviceType":"desktop"}}
                        ],"referrers":[
                          {"count":5,"dimensions":{"refererHost":"example.com"}}
                        ]}]}},"errors":null}
                        """, MediaType.APPLICATION_JSON));

        var result = new CloudflareAnalyticsService(client, "test-api-token", "account-id", "site-tag")
                .getPageLoads();

        assertEquals(15, result.pageLoads());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.AnalyticsDimension("IT", 12),
                new CloudflareAnalyticsService.AnalyticsDimension("FR", 3)), result.countries());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.DailyPageLoads("2026-11-24", 8),
                new CloudflareAnalyticsService.DailyPageLoads("2026-11-25", 7)), result.daily());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.AnalyticsDimension("/", 9),
                new CloudflareAnalyticsService.AnalyticsDimension("/survey", 6)), result.topPages());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.AnalyticsDimension("Chrome", 10)), result.browsers());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.AnalyticsDimension("desktop", 15)), result.devices());
        assertEquals(java.util.List.of(
                new CloudflareAnalyticsService.AnalyticsDimension("example.com", 5)), result.referrers());
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
}
