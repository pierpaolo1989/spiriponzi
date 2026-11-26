package com.soa.spiriponzi.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.soa.spiriponzi.service.PromoCodeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PromoCodeAdminController {
    private final PromoCodeService promoCodeService;

    @GetMapping("/admin/promo-codes")
    public String promoCodes(Model model) {
        model.addAttribute("promoCodes", promoCodeService.listCodes());
        return "admin/promo-codes";
    }

    @PostMapping("/admin/promo-codes")
    public String createCode(
            @RequestParam int points,
            @RequestParam int maxRedemptions,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
            RedirectAttributes flash) {
        try {
            promoCodeService.createCode(points, maxRedemptions, expiresOn);
            flash.addFlashAttribute("message", "Codice promozionale creato.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/admin/promo-codes";
    }
}
