package com.soa.spiriponzi.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.OffsetDateTime;
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
            query WebAnalytics($accountTag: string!, $siteTag: string!, $start: string!, $end: string!) {
              viewer {
                accounts(filter: { accountTag: $accountTag }) {
                  rumPageloadEventsAdaptiveGroups(
                    limit: 500
                    filter: { datetime_geq: $start, datetime_lt: $end, siteTag: $siteTag }
                    orderBy: [count_DESC]
                  ) {
                    count
                    dimensions { %s }
                  }
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

        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        Map<String, Object> variables = Map.of(
                "accountTag", accountId,
                "siteTag", siteTag,
                "start", end.minusDays(30).toString(),
                "end", end.toString());

        JsonNode response = request(variables, "country");
        if (hasCountryFieldError(response)) {
            response = request(variables, "clientCountryName");
        }
        if (hasGraphQlErrors(response)) {
            log.warn("Cloudflare Analytics returned GraphQL errors: {}", graphQlErrorSummary(response));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Cloudflare Analytics non valida");
        }

        JsonNode accounts = response.path("data").path("viewer").path("accounts");
        if (!accounts.isArray() || accounts.isEmpty()) {
            throw invalidResponse();
        }
        JsonNode groups = accounts.get(0).path("rumPageloadEventsAdaptiveGroups");
        if (!groups.isArray()) {
            throw invalidResponse();
        }

        Map<String, Long> countryTotals = new LinkedHashMap<>();
        long pageLoads = 0;
        for (JsonNode group : groups) {
            JsonNode count = group.path("count");
            if (!count.isIntegralNumber() || !count.canConvertToLong() || count.longValue() < 0) {
                throw invalidResponse();
            }
            String country = countryFrom(group.path("dimensions"));
            long countryPageLoads = count.longValue();
            long countryTotal = countryTotals.getOrDefault(country, 0L);
            if (Long.MAX_VALUE - countryTotal < countryPageLoads
                    || Long.MAX_VALUE - pageLoads < countryPageLoads) {
                throw invalidResponse();
            }
            countryTotals.put(country, countryTotal + countryPageLoads);
            pageLoads += countryPageLoads;
        }

        List<CountryPageLoads> countries = new ArrayList<>();
        countryTotals.forEach((country, count) -> countries.add(new CountryPageLoads(country, count)));
        countries.sort(Comparator.comparingLong(CountryPageLoads::pageLoads).reversed());
        return new AnalyticsSnapshot(pageLoads, List.copyOf(countries));
    }

    private JsonNode request(Map<String, Object> variables, String countryField) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "query", QUERY.formatted(countryField),
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

    private boolean hasCountryFieldError(JsonNode response) {
        JsonNode errors = response.path("errors");
        if (!errors.isArray()) {
            return false;
        }
        for (JsonNode error : errors) {
            String message = error.path("message").asText("");
            if (message.contains("country") && message.contains("field")) {
                return true;
            }
        }
        return false;
    }

    private String countryFrom(JsonNode dimensions) {
        JsonNode country = dimensions.path("country");
        if (country.isTextual()) {
            return country.asText();
        }
        country = dimensions.path("clientCountryName");
        return country.isTextual() ? country.asText() : "XX";
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Cloudflare Analytics non valida");
    }

    public record AnalyticsSnapshot(long pageLoads, List<CountryPageLoads> countries) {}

    public record CountryPageLoads(String country, long pageLoads) {}

    private static class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
