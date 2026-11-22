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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RenderServiceTest {
    private RestTemplate client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new RestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
    }

    @Test
    void listsOnlyTheRequiredServiceFields() {
        server.expect(requestTo("https://api.render.com/v1/services?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("""
                        [{"cursor":"cursor","service":{"name":"web","type":"web_service",
                         "suspended":"not_suspended","secret":"not returned"}}]
                        """, MediaType.APPLICATION_JSON));
        var result = new RenderService(client, "test-key").listServices();
        assertEquals(java.util.List.of(new RenderService.RenderServiceInfo("web", "web_service", "not_suspended")), result);
        server.verify();
    }

    @Test
    void missingKeyDoesNotCallRender() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> new RenderService(client, "").listServices());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        server.verify();
    }

    @Test
    void apiErrorsAndMalformedResponsesAreNotTreatedAsEmpty() {
        server.expect(requestTo("https://api.render.com/v1/services?limit=100"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var service = new RenderService(client, "test-key");
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, service::listServices).getStatusCode());
        server.verify();
        server.reset();
        server.expect(requestTo("https://api.render.com/v1/services?limit=100"))
                .andRespond(withSuccess("[{\"service\":{\"name\":\"web\"}}]", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, service::listServices).getStatusCode());
        server.verify();
    }
}
