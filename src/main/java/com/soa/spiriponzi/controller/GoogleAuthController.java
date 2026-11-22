package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@ConditionalOnProperty(name = "app.google.enabled", havingValue = "true")
public class GoogleAuthController {
    public static final String REGISTRATION_REFERRAL = GoogleAuthController.class.getName() + ".referral";

    private final UserService userService;

    public GoogleAuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/auth/google/register")
    public String register(@RequestParam(value = "terms", defaultValue = "false") boolean terms,
            @RequestParam(value = "referralCode", defaultValue = "") String referralCode,
            HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute(REGISTRATION_REFERRAL);
        if (!terms || !userService.isValidReferralCode(referralCode)) {
            redirectAttributes.addAttribute("ref", referralCode);
            redirectAttributes.addAttribute(!terms ? "googleTerms" : "googleReferral", "");
            return "redirect:/register";
        }
        session.setAttribute(REGISTRATION_REFERRAL, referralCode);
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping("/auth/google/login")
    public String login(HttpSession session) {
        session.removeAttribute(REGISTRATION_REFERRAL);
        return "redirect:/oauth2/authorization/google";
    }
}
