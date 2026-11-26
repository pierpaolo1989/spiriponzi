package com.soa.spiriponzi.controller;

import java.security.Principal;

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
            flash.addFlashAttribute("message", "Codice riscattato: ti sono stati accreditati "
                    + points + " punti.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/promotions";
    }
}
