package com.soa.spiriponzi.controller.rest;

import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user-ads")
public class UserAdController {
	
    private final ADService adService;
    private final UserService userService;

    @PostMapping
    public void createAd(@RequestBody UserADDto adDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        adService.create(user, adDto);
    }

    @DeleteMapping("/{id}")
    public void deleteAd(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        adService.delete(user, id);
    }

}
