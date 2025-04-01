package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AdController {

    private final UserService userService;

    public AdController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/create-ads")
    public String surfAds(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        return "create-ads";
    }
}
