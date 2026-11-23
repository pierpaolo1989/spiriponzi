package com.soa.spiriponzi.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class GoogleProfileModelAdvice {

    @ModelAttribute("googleProfilePicture")
    public String googleProfilePicture(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return null;
        }
        Object picture = oidcUser.getAttribute("picture");
        return picture instanceof String pictureUrl && pictureUrl.startsWith("https://")
                ? pictureUrl
                : null;
    }
}
