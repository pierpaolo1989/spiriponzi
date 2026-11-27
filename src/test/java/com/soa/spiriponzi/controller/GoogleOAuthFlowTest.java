package com.soa.spiriponzi.controller;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.model.Role;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.security.GoogleOidcUserService;
import com.soa.spiriponzi.service.OfferwallService;
import com.soa.spiriponzi.service.TheoremReachCallbackService;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.WithdrawService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, GoogleAuthController.class})
@Import({SecurityConfig.class, GoogleOidcUserService.class, GoogleOAuthFlowTest.ProviderConfig.class})
@TestPropertySource(properties = "app.google.enabled=true")
class GoogleOAuthFlowTest {
    private static final RSAKey KEY;
    private static final HttpServer PROVIDER;
    private static final String ISSUER;
    private static final AtomicReference<String> ID_TOKEN = new AtomicReference<>();

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ISSUER = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
            PROVIDER.createContext("/keys", exchange -> respond(exchange,
                    new JWKSet(KEY.toPublicJWK()).toString()));
            PROVIDER.createContext("/token", exchange -> respond(exchange,
                    "{\"access_token\":\"access\",\"token_type\":\"Bearer\",\"expires_in\":300,\"id_token\":\""
                            + ID_TOKEN.get() + "\"}"));
            PROVIDER.createContext("/userinfo", exchange -> respond(exchange,
                    "{\"sub\":\"google-sub\",\"email\":\"new@gmail.com\",\"email_verified\":true,"
                            + "\"picture\":\"https://lh3.googleusercontent.com/test-photo\"}"));
            PROVIDER.start();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @TestConfiguration
    static class ProviderConfig {
        @Bean
        ClientRegistrationRepository testClientRegistrations() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                    .clientId("test-client").clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost/login/oauth2/code/google")
                    .scope("openid", "email", "profile")
                    .authorizationUri(ISSUER + "/authorize").tokenUri(ISSUER + "/token")
                    .jwkSetUri(ISSUER + "/keys").issuerUri(ISSUER)
                    .userInfoUri(ISSUER + "/userinfo").userNameAttributeName("sub")
                    .clientName("Test Google").build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }

    @Autowired private MockMvc mvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserService users;
    @MockBean private WithdrawService withdrawals;
    @MockBean private OfferwallService offerwall;
    @MockBean private TheoremReachCallbackService theoremReachCallback;

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    private MvcResult callback(MockHttpSession session) throws Exception {
        MvcResult start = mvc.perform(get("/oauth2/authorization/google")
                .servletPath("/oauth2/authorization/google").session(session))
                .andExpect(status().is3xxRedirection()).andReturn();
        var params = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
                .build().getQueryParams();
        String nonce = params.getFirst("nonce");
        String state = java.net.URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8);
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject("google-sub")
                .audience("test-client").issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("nonce", nonce).claim("email", "new@gmail.com").claim("email_verified", true).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        ID_TOKEN.set(jwt.serialize());
        return mvc.perform(get("/login/oauth2/code/google").servletPath("/login/oauth2/code/google")
                .session(session).param("code", "test-code").param("state", state)).andReturn();
    }

    @Test
    void registrationCompletesOidcExchangeAndCreatesLocalSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(users.isValidReferralCode("REF123")).thenReturn(true);
        Role role = new Role();
        role.setName("ROLE_USER");
        User signedIn = User.builder().id(1L).email("new@gmail.com").enabled(true).roles(List.of(role))
                .userStats(UserStats.builder().build()).build();
        when(users.signInWithGoogle("google-sub", "new@gmail.com", true, "REF123")).thenReturn(signedIn);
        when(users.findByEmail("new@gmail.com")).thenReturn(signedIn);
        when(users.getOrCreateReferralCode(1L)).thenReturn("REF123");
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .with(csrf()).param("terms", "true").param("referralCode", "REF123"))
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        MvcResult result = callback(session);
        assertEquals("/dashboard", result.getResponse().getRedirectedUrl());
        SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertEquals("new@gmail.com", context.getAuthentication().getName());
        assertEquals("https://lh3.googleusercontent.com/test-photo",
                ((org.springframework.security.oauth2.core.oidc.user.OidcUser)
                        context.getAuthentication().getPrincipal()).getAttribute("picture"));
        assertTrue(context.getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        String profileHtml = mvc.perform(get("/profile").servletPath("/profile").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(profileHtml.contains("src=\"https://lh3.googleusercontent.com/test-photo\""));
        assertTrue(profileHtml.contains("alt=\"Google profile picture\""));
        verify(users).signInWithGoogle("google-sub", "new@gmail.com", true, "REF123");
    }

    @Test
    void googleRegistrationShowsActivationMessageBeforeSignIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(users.isValidReferralCode("")).thenReturn(true);
        Role role = new Role();
        role.setName("ROLE_USER");
        User unverified = User.builder().id(2L).email("new@gmail.com").enabled(false).roles(List.of(role))
                .userStats(UserStats.builder().build()).build();
        when(users.signInWithGoogle("google-sub", "new@gmail.com", true, "")).thenReturn(unverified);
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .with(csrf()).param("terms", "true"))
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        assertEquals("/register?googleActivation", callback(session).getResponse().getRedirectedUrl());
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/register").servletPath("/register").param("googleActivation", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Google registration successful")))
                .andExpect(content().string(containsString("confirm your account before signing in")));
        verify(users).signInWithGoogle("google-sub", "new@gmail.com", true, "");
    }

    @Test
    void existingPasswordAccountIsNotLinkedAfterOidcExchange() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(users.isValidReferralCode("")).thenReturn(true);
        when(users.signInWithGoogle("google-sub", "new@gmail.com", true, ""))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists"));
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .with(csrf()).param("terms", "true"))
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        assertEquals("/login?googleError", callback(session).getResponse().getRedirectedUrl());
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
    }
}
