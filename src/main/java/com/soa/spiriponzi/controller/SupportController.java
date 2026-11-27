package com.soa.spiriponzi.controller;

import java.security.Principal;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.soa.spiriponzi.dto.SupportTicketForm;
import com.soa.spiriponzi.exception.InvalidReCaptchaException;
import com.soa.spiriponzi.exception.ReCaptchaInvalidException;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.service.EmailService;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SupportController {
    private static final Map<String, String> TOPIC_LABELS = Map.of(
            "MISSING_CREDIT", "Missing credit / points not credited",
            "ACCOUNT_ACCESS", "Account access or activation",
            "WITHDRAWAL", "Withdrawal",
            "TECHNICAL_ISSUE", "Technical issue",
            "SUGGESTION", "Suggestion",
            "OTHER", "Other");

    private final EmailService emailService;
    private final CaptchaService captchaService;

    @GetMapping("/support")
    public String support(Model model) {
        model.addAttribute("supportTicket", new SupportTicketForm());
        model.addAttribute("captchaSiteKey", captchaService.getReCaptchaSite());
        return "support";
    }

    @PostMapping("/support")
    public String submitSupportTicket(
            @Valid @ModelAttribute("supportTicket") SupportTicketForm form,
            BindingResult bindingResult,
            Principal principal,
            HttpServletRequest request,
            Model model,
            RedirectAttributes flash) {
        if (form.getTopic() == null || !TOPIC_LABELS.containsKey(form.getTopic())) {
            bindingResult.rejectValue("topic", "support.topic", "Select one of the listed topics.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("captchaSiteKey", captchaService.getReCaptchaSite());
            return "support";
        }

        String siteKey = captchaService.getReCaptchaSite();
        String secretKey = captchaService.getReCaptchaSecret();
        if (!StringUtils.hasText(siteKey) || !StringUtils.hasText(secretKey)
                || "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI".equals(siteKey)
                || "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe".equals(secretKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Support CAPTCHA is not configured");
        }

        try {
            captchaService.processResponse(request.getParameter("g-recaptcha-response"));
        } catch (InvalidReCaptchaException | ReCaptchaInvalidException ex) {
            model.addAttribute("captchaError", "Please complete the CAPTCHA verification and try again.");
            model.addAttribute("captchaSiteKey", siteKey);
            return "support";
        }

        try {
            emailService.sendSupportTicket(
                    principal.getName(), TOPIC_LABELS.get(form.getTopic()), form.getMessage().trim());
        } catch (MessagingException ex) {
            log.error("Unable to send a support ticket email", ex);
            model.addAttribute("sendError", "We could not send your request. Please try again later.");
            model.addAttribute("captchaSiteKey", siteKey);
            return "support";
        }

        flash.addFlashAttribute("message", "Your support request has been sent. Our team will reply to your account email.");
        return "redirect:/support";
    }
}
