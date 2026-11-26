package com.soa.spiriponzi.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CloudflareAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(CloudflareAnalyticsService.class);
    private static final String GRAPHQL_URL = "https://api.cloudflare.com/client/v4/graphql";
    private static final String QUERY = """
            query WebAnalytics($accountTag: String!, $siteTag: String!, $start: Date!, $end: Date!) {
              viewer {
                accounts(filter: { accountTag: $accountTag }) {
                  daily: rumPageloadEventsAdaptiveGroups(
                    limit: 30
                    filter: { date_geq: $start, date_leq: $end, siteTag: $siteTag }
                    orderBy: [date_ASC]
                  ) { count dimensions { date } }
                  countries: rumPageloadEventsAdaptiveGroups(
                    limit: 500
                    filter: { date_geq: $start, date_leq: $end, siteTag: $siteTag }
                    orderBy: [count_DESC]
                  ) { count dimensions { countryName } }
                  topPages: rumPageloadEventsAdaptiveGroups(
                    limit: 10
                    filter: { date_geq: $start, date_leq: $end, siteTag: $siteTag }
                    orderBy: [count_DESC]
                  ) { count dimensions { requestPath } }
                  browsers: rumPageloadEventsAdaptiveGroups(
                    limit: 10
                    filter: { date_geq: $start, date_leq: $end, siteTag: $siteTag }
                    orderBy: [count_DESC]
                  ) { count dimensions { userAgentBrowser } }
                  devices: rumPageloadEventsAdaptiveGroups(
                    limit: 10
                    filter: { date_geq: $start, date_leq: $end, siteTag: $siteTag }
                    orderBy: [count_DESC]
                  ) { count dimensions { deviceType } }
                  referrers: rumPageloadEventsAdaptiveGroups(
                    limit: 10
                    filter: { date_geq: $start, date_leq: $end, siteTag: $siteTag }
                    orderBy: [count_DESC]
                  ) { count dimensions { refererHost } }
                }
              }
            }
            """;

    private final RestTemplate client;
    private final String apiToken;
    private final String accountId;
    private final String siteTag;

    @Autowired
    public CloudflareAnalyticsService(
            RestTemplateBuilder builder,
            @Value("${cloudflare.analytics.api-token:}") String apiToken,
            @Value("${cloudflare.analytics.account-id:}") String accountId,
            @Value("${cloudflare.web-analytics.token:}") String siteTag) {
        this(builder.requestFactory(NoRedirectRequestFactory::new)
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5)).build(), apiToken, accountId, siteTag);
    }

    CloudflareAnalyticsService(RestTemplate client, String apiToken, String accountId, String siteTag) {
        this.client = client;
        this.apiToken = apiToken;
        this.accountId = accountId;
        this.siteTag = siteTag;
    }

    public AnalyticsSnapshot getPageLoads() {
        if (apiToken.isBlank() || accountId.isBlank() || siteTag.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cloudflare Analytics non configurata");
        }

        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        Map<String, Object> variables = Map.of(
                "accountTag", accountId,
                "siteTag", siteTag,
                "start", end.minusDays(29).toString(),
                "end", end.toString());

        JsonNode response = request(variables);
        if (hasGraphQlErrors(response)) {
            log.warn("Cloudflare Analytics returned GraphQL errors: {}", graphQlErrorSummary(response));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Cloudflare Analytics non valida");
        }

        JsonNode accounts = response.path("data").path("viewer").path("accounts");
        if (!accounts.isArray() || accounts.isEmpty()) {
            throw invalidResponse();
        }
        JsonNode account = accounts.get(0);
        List<DailyPageLoads> daily = dailyPageLoads(account.path("daily"));
        long pageLoads = 0;
        for (DailyPageLoads day : daily) {
            if (Long.MAX_VALUE - pageLoads < day.pageLoads()) {
                throw invalidResponse();
            }
            pageLoads += day.pageLoads();
        }

        return new AnalyticsSnapshot(
                pageLoads,
                dimensionPageLoads(account.path("countries"), "countryName", "Unknown"),
                daily,
                dimensionPageLoads(account.path("topPages"), "requestPath", "/"),
                dimensionPageLoads(account.path("browsers"), "userAgentBrowser", "Unknown"),
                dimensionPageLoads(account.path("devices"), "deviceType", "Unknown"),
                dimensionPageLoads(account.path("referrers"), "refererHost", "Direct"));
    }

    private List<DailyPageLoads> dailyPageLoads(JsonNode groups) {
        if (!groups.isArray()) {
            throw invalidResponse();
        }
        List<DailyPageLoads> days = new ArrayList<>();
        for (JsonNode group : groups) {
            long count = pageLoadCount(group);
            String date = group.path("dimensions").path("date").asText("");
            if (date.isBlank()) {
                throw invalidResponse();
            }
            days.add(new DailyPageLoads(date, count));
        }
        days.sort(Comparator.comparing(DailyPageLoads::date));
        return List.copyOf(days);
    }

    private List<AnalyticsDimension> dimensionPageLoads(JsonNode groups, String dimension, String fallback) {
        if (!groups.isArray()) {
            throw invalidResponse();
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        for (JsonNode group : groups) {
            long count = pageLoadCount(group);
            String value = group.path("dimensions").path(dimension).asText("").trim();
            if (value.isEmpty()) {
                value = fallback;
            }
            long currentTotal = totals.getOrDefault(value, 0L);
            if (Long.MAX_VALUE - currentTotal < count) {
                throw invalidResponse();
            }
            totals.put(value, currentTotal + count);
        }
        List<AnalyticsDimension> dimensions = new ArrayList<>();
        totals.forEach((value, count) -> dimensions.add(new AnalyticsDimension(value, count)));
        dimensions.sort(Comparator.comparingLong(AnalyticsDimension::pageLoads).reversed());
        return List.copyOf(dimensions);
    }

    private long pageLoadCount(JsonNode group) {
        JsonNode count = group.path("count");
        if (!count.isIntegralNumber() || !count.canConvertToLong() || count.longValue() < 0) {
            throw invalidResponse();
        }
        return count.longValue();
    }

    private JsonNode request(Map<String, Object> variables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "query", QUERY,
                "variables", variables);
        try {
            ResponseEntity<JsonNode> result = client.exchange(
                    GRAPHQL_URL, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            if (!result.getStatusCode().is2xxSuccessful() || result.getBody() == null) {
                throw invalidResponse();
            }
            return result.getBody();
        } catch (RestClientException ex) {
            log.warn("Impossibile contattare Cloudflare Analytics ({})", ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossibile contattare Cloudflare Analytics");
        }
    }

    private boolean hasGraphQlErrors(JsonNode response) {
        JsonNode errors = response.path("errors");
        return errors.isArray() && !errors.isEmpty();
    }

    private String graphQlErrorSummary(JsonNode response) {
        JsonNode errors = response.path("errors");
        List<String> messages = new ArrayList<>();
        for (JsonNode error : errors) {
            String message = error.path("message").asText("").replaceAll("\\s+", " ").trim();
            if (!message.isEmpty()) {
                messages.add(message.length() > 500 ? message.substring(0, 500) : message);
            }
        }
        if (messages.isEmpty()) {
            return "Cloudflare response contained errors without messages";
        }
        int displayedCount = Math.min(messages.size(), 5);
        String summary = String.join(" | ", messages.subList(0, displayedCount));
        return messages.size() > displayedCount ? summary + " | additional errors omitted" : summary;
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Cloudflare Analytics non valida");
    }

    public record AnalyticsSnapshot(
            long pageLoads,
            List<AnalyticsDimension> countries,
            List<DailyPageLoads> daily,
            List<AnalyticsDimension> topPages,
            List<AnalyticsDimension> browsers,
            List<AnalyticsDimension> devices,
            List<AnalyticsDimension> referrers) {}

    public record AnalyticsDimension(String value, long pageLoads) {}

    public record DailyPageLoads(String date, long pageLoads) {}

    private static class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
