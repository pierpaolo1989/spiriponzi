package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.config.GoogleOAuthConfig;
import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.security.GoogleOidcUserService;
import com.soa.spiriponzi.service.OfferwallService;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.WithdrawService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, GoogleAuthController.class})
@Import({SecurityConfig.class, GoogleOAuthConfig.class, GoogleOidcUserService.class})
@TestPropertySource(properties = {"app.google.enabled=true", "app.google.client-id=test-client",
        "app.google.client-secret=test-secret", "app.public-base-url=https://example.com"})
class GoogleAuthWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserService users;
    @MockBean private WithdrawService withdrawals;
    @MockBean private OfferwallService offerwall;

    @Test
    void registrationRequiresCsrfTermsAndValidReferral() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/register").servletPath("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Continue with Google")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("formaction=\"/auth/google/register\"")));
        mvc.perform(get("/login").servletPath("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in with Google")));
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .param("terms", "true")).andExpect(status().isForbidden());
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/register?*googleTerms*"));
        when(users.isValidReferralCode("bad")).thenReturn(false);
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .with(csrf()).param("terms", "true").param("referralCode", "bad"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/register?*googleReferral*"));
        assertNull(session.getAttribute(GoogleAuthController.REGISTRATION_REFERRAL));

        when(users.isValidReferralCode("REF123")).thenReturn(true);
        mvc.perform(post("/auth/google/register").servletPath("/auth/google/register").session(session)
                .with(csrf()).param("terms", "true").param("referralCode", "REF123"))
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
        assertEquals("REF123", session.getAttribute(GoogleAuthController.REGISTRATION_REFERRAL));
        MvcResult authorization = mvc.perform(get("/oauth2/authorization/google")
                .servletPath("/oauth2/authorization/google").session(session))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = authorization.getResponse().getRedirectedUrl();
        assertTrue(location.startsWith("https://accounts.google.com/"));
        assertTrue(location.contains("state="));
        assertTrue(location.contains("nonce="));
        assertEquals("https://example.com/login/oauth2/code/google",
                org.springframework.web.util.UriComponentsBuilder.fromUriString(location).build()
                        .getQueryParams().getFirst("redirect_uri"));
    }

    @Test
    void loginClearsPendingRegistrationAndInvalidCallbackFails() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleAuthController.REGISTRATION_REFERRAL, "REF123");
        mvc.perform(get("/auth/google/login").servletPath("/auth/google/login").session(session))
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
        assertNull(session.getAttribute(GoogleAuthController.REGISTRATION_REFERRAL));
        mvc.perform(get("/login/oauth2/code/google").servletPath("/login/oauth2/code/google")
                .session(session).param("code", "fake").param("state", "invalid"))
                .andExpect(redirectedUrl("/login?googleError"));
        verify(users, never()).signInWithGoogle(anyString(), anyString(), anyBoolean(), any());
    }
}
