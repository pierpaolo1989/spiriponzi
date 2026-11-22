package com.soa.spiriponzi.service;

import java.time.Duration;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

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
public class RenderService {
    private static final Logger log = LoggerFactory.getLogger(RenderService.class);
    private static final String SERVICES_URL = "https://api.render.com/v1/services?limit=100";
    private final RestTemplate client;
    private final String apiKey;

    @Autowired
    public RenderService(RestTemplateBuilder builder, @Value("${render.api-key:}") String apiKey) {
        this(builder.requestFactory(NoRedirectRequestFactory::new)
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5)).build(), apiKey);
    }

    RenderService(RestTemplate client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey;
    }

    public List<RenderServiceInfo> listServices() {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Render API non configurata");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        JsonNode response;
        try {
            ResponseEntity<JsonNode> result = client.exchange(
                    SERVICES_URL, HttpMethod.GET, new HttpEntity<Void>(headers), JsonNode.class);
            if (!result.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Render non valida");
            }
            response = result.getBody();
        } catch (RestClientException ex) {
            log.warn("Impossibile recuperare i servizi Render ({})", ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossibile contattare Render");
        }
        if (response == null || !response.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Render non valida");
        }
        List<RenderServiceInfo> services = new ArrayList<>();
        for (JsonNode item : response) {
            JsonNode service = item.path("service");
            JsonNode name = service.path("name");
            JsonNode type = service.path("type");
            JsonNode suspended = service.path("suspended");
            if (!name.isTextual() || !type.isTextual() || !suspended.isTextual()
                    || !(suspended.asText().equals("suspended") || suspended.asText().equals("not_suspended"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Risposta Render non valida");
            }
            services.add(new RenderServiceInfo(name.asText(), type.asText(), suspended.asText()));
        }
        return List.copyOf(services);
    }

    public record RenderServiceInfo(String name, String type, String suspended) {}

    private static class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
