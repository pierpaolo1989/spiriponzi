package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@Controller
public class FragmentController {

    private final ADService adService;
    private final UserService userService;

    @GetMapping("/fragment/user-ads")
    public String getAdsFragment(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        model.addAttribute("ads", adService.getProposedAds(user.getId()));
        return "fragments/ads :: adList";
    }

    @GetMapping("/fragment/summary")
    public String getSummaryFragment(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final User user = userService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        return "fragments/summary :: summary";
    }
}
