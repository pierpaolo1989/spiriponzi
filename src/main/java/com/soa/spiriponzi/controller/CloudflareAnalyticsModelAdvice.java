package com.soa.spiriponzi.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class CloudflareAnalyticsModelAdvice {

    private final String analyticsToken;

    public CloudflareAnalyticsModelAdvice(
            @Value("${cloudflare.web-analytics.token:}") String analyticsToken) {
        this.analyticsToken = analyticsToken;
    }

    @ModelAttribute("cloudflareAnalyticsToken")
    public String cloudflareAnalyticsToken() {
        return analyticsToken.isBlank() ? null : analyticsToken;
    }
}
