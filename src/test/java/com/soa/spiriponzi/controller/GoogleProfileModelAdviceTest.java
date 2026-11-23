package com.soa.spiriponzi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleProfileModelAdviceTest {
    private final GoogleProfileModelAdvice advice = new GoogleProfileModelAdvice();

    @Test
    void exposesSecureGoogleProfilePicture() {
        Authentication authentication = mock(Authentication.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getAttribute("picture")).thenReturn("https://lh3.googleusercontent.com/photo");

        assertEquals("https://lh3.googleusercontent.com/photo", advice.googleProfilePicture(authentication));
    }

    @Test
    void omitsMissingOrInsecurePictures() {
        Authentication authentication = mock(Authentication.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getAttribute("picture")).thenReturn("http://example.com/photo");

        assertNull(advice.googleProfilePicture(authentication));
        assertNull(advice.googleProfilePicture(null));
    }
}
