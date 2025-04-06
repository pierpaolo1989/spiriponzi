package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class AdController {

    private final UserService userService;
    private final ADService adService;

    @GetMapping("/create-ads")
    public String surfAds(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        model.addAttribute("ads", adService.findSurfedAdsByUser(user.getId()));
        return "create-ads";
    }

    @PostMapping("/ads")
    @ResponseBody
    public String createUserAd(@RequestParam("idAd") Long idAd) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        userService.createUserAd(user, idAd);
        return "ok";
    }

    @GetMapping("/get-ads")
    @ResponseBody
    public List<UserADDto> getAds(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        return adService.findSurfedAdsByUser(user.getId());
    }

    @GetMapping("/ads/proposed")
    @ResponseBody
    public List<ADDto> getProposedAds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        return adService.getProposedAds(user.getId());
    }
}
