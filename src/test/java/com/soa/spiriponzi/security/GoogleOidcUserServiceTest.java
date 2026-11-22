package com.soa.spiriponzi.security;

import java.time.Instant;
import java.util.List;

import com.soa.spiriponzi.controller.GoogleAuthController;
import com.soa.spiriponzi.model.Role;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GoogleOidcUserServiceTest {
    private UserService users;
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private GoogleOidcUserService service;
    private MockHttpServletRequest request;
    private OidcUserRequest oidcRequest;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        delegate = mock(OAuth2UserService.class);
        service = new GoogleOidcUserService(users, delegate);
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        oidcRequest = mock(OidcUserRequest.class);
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private OidcUser googleUser(boolean verified) {
        OidcIdToken token = OidcIdToken.withTokenValue("token")
                .subject("google-sub").claim("email", "google@gmail.com")
                .claim("email_verified", verified).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).build();
        return new DefaultOidcUser(List.of(), token);
    }

    @Test
    void registrationUsesVerifiedGoogleIdentityAndLocalAccountName() {
        when(delegate.loadUser(oidcRequest)).thenReturn(googleUser(true));
        request.getSession().setAttribute(GoogleAuthController.REGISTRATION_REFERRAL, "REF123");
        Role role = new Role();
        role.setName("ROLE_USER");
        User local = User.builder().email("local@example.com").roles(List.of(role)).build();
        when(users.signInWithGoogle("google-sub", "google@gmail.com", true, "REF123")).thenReturn(local);

        OidcUser result = service.loadUser(oidcRequest);

        assertEquals("local@example.com", result.getName());
        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertNull(request.getSession().getAttribute(GoogleAuthController.REGISTRATION_REFERRAL));
    }

    @Test
    void rejectsUnverifiedEmailWithoutAccessingAccounts() {
        when(delegate.loadUser(oidcRequest)).thenReturn(googleUser(false));
        assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(oidcRequest));
        verifyNoInteractions(users);
    }

    @Test
    void loginWithoutRegistrationDoesNotCreateAnAccount() {
        when(delegate.loadUser(oidcRequest)).thenReturn(googleUser(true));
        Role role = new Role();
        role.setName("ROLE_USER");
        when(users.signInWithGoogle("google-sub", "google@gmail.com", false, null))
                .thenReturn(User.builder().email("google@gmail.com").roles(List.of(role)).build());
        assertEquals("google@gmail.com", service.loadUser(oidcRequest).getName());
        verify(users).signInWithGoogle("google-sub", "google@gmail.com", false, null);
    }
}
