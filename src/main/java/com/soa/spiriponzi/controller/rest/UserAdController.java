package com.soa.spiriponzi.controller.rest;

import com.soa.spiriponzi.dto.ClaimDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.service.ClaimService;
import com.soa.spiriponzi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user-ads")
public class UserAdController {
	
    private final ADService adService;
    private final UserService userService;

    @ResponseBody
    @PostMapping
    public void createAd(@RequestBody UserADDto adDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        adService.create(user, adDto);
    }

}
