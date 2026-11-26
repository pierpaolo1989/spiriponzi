package com.soa.spiriponzi.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.soa.spiriponzi.service.PromoCodeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PromoCodeController {
    private final PromoCodeService promoCodeService;

    @GetMapping("/promotions")
    public String promotions() {
        return "promotions";
    }

    @PostMapping("/promotions")
    public String redeemCode(@RequestParam String code, Principal principal, RedirectAttributes flash) {
        try {
            int points = promoCodeService.redeemCode(code, principal.getName());
            flash.addFlashAttribute("message",
                    "Promo code redeemed: " + points + " points have been added to your balance.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", userFacingError(ex));
        }
        return "redirect:/promotions";
    }

    private String userFacingError(ResponseStatusException exception) {
        if (exception.getStatusCode() == HttpStatus.GONE) {
            return "This promo code has expired or reached its redemption limit.";
        }
        if (exception.getStatusCode() == HttpStatus.CONFLICT) {
            return "This code has already been redeemed by your account, or your balance cannot accept more points.";
        }
        if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return "Your account could not be found.";
        }
        if (exception.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return "The promo code is missing or invalid.";
        }
        return "We could not redeem this promo code. Please try again later.";
    }
}
