package com.soa.spiriponzi.controller;

import java.net.URI;
import java.security.Principal;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.AffiliateService;
import com.soa.spiriponzi.service.UserService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AffiliateController {

    private static final String BANNER_PATH = "/img/affiliate-banner.svg";

    private final AffiliateService affiliateService;
    private final UserService userService;

    @Value("${app.public-base-url:https://www.spiriponzi.cloud}")
    private String publicBaseUrl;

    @GetMapping("/affiliate")
    public String affiliate(Principal principal, Model model) {
        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        model.addAttribute("user", user);

        String referralCode = userService.getOrCreateReferralCode(user.getId());
        String baseUrl = publicBaseUrl.replaceAll("/+$", "");
        String clickUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/affiliate/click/{code}")
                .buildAndExpand(referralCode)
                .toUriString();
        String bannerUrl = baseUrl + BANNER_PATH;
        String embedCode = "<a href=\"" + clickUrl + "\" target=\"_blank\" rel=\"noopener noreferrer\">"
                + "<img src=\"" + bannerUrl
                + "\" alt=\"Join Spiriponzi\" width=\"468\" height=\"60\""
                + " style=\"max-width:100%;height:auto;border:0\"></a>";
        AffiliateService.AffiliateStats stats = affiliateService.getStats(user.getId());

        model.addAttribute("referralCode", referralCode);
        model.addAttribute("affiliateClickUrl", clickUrl);
        model.addAttribute("affiliateBannerUrl", bannerUrl);
        model.addAttribute("affiliateEmbedCode", embedCode);
        model.addAttribute("qualifiedClicks", stats.qualifiedClicks());
        model.addAttribute("affiliateCoinsEarned", stats.coinsEarned());
        model.addAttribute("clicksUntilReward", stats.clicksUntilReward());
        return "affiliate";
    }

    @GetMapping("/affiliate/click/{referralCode}")
    public ResponseEntity<Void> trackClick(
            @PathVariable String referralCode,
            @CookieValue(value = AffiliateService.VISITOR_COOKIE_NAME, required = false) String visitorToken,
            Principal principal,
            HttpServletRequest request) {
        AffiliateService.ClickResult result = affiliateService.trackClick(
                referralCode, visitorToken, principal == null ? null : principal.getName());
        URI registrationUrl = UriComponentsBuilder.fromPath("/register")
                .queryParam("ref", "{code}")
                .buildAndExpand(referralCode)
                .encode()
                .toUri();

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND).location(registrationUrl);
        if (result.setVisitorCookie()) {
            ResponseCookie cookie = ResponseCookie.from(AffiliateService.VISITOR_COOKIE_NAME, result.visitorToken())
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofDays(365))
                    .build();
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return response.build();
    }
}
