package com.soa.spiriponzi.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ControllerTestSupport {

    public static final String EMAIL = "user@example.com";

    private ControllerTestSupport() {
    }

    public static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, "password")
        );
    }
}
