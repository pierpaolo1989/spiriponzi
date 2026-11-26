package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.AdminMfaService;
import com.soa.spiriponzi.service.AdminMfaService.Status;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AdminMfaController {

    private final AdminMfaService adminMfaService;
    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/admin/mfa")
    public String verificationPage(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return adminMfaService.hasPendingChallenge(request.getSession(false))
                ? "admin-mfa"
                : "redirect:/login";
    }

    @PostMapping("/admin/mfa")
    public String verifyCode(
            @RequestParam(required = false) String code,
            HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        var result = adminMfaService.verify(session, code);
        if (result.status() == Status.INVALID) {
            return "redirect:/admin/mfa?error";
        }
        if (result.status() != Status.VERIFIED) {
            return "redirect:/login?mfaExpired";
        }

        UserDetails user = userDetailsService.loadUserByUsername(result.username());
        if (!user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            adminMfaService.clearChallenge(session);
            return "redirect:/login?mfaExpired";
        }

        request.changeSessionId();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return "redirect:/admin";
    }
}
