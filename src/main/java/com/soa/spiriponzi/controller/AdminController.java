package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.enums.WithdrawStatus;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AdRepository adRepository;
    private final ClaimRepository claimRepository;
    private final WithdrawRepository withdrawRepository;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalBalance", userRepository.sumBalances());
        model.addAttribute("activeAds", adRepository.countByActiveTrueAndDeletedFalse());
        model.addAttribute("totalClaims", claimRepository.count());
        model.addAttribute("totalClaimAmount", claimRepository.sumClaimAmounts());
        model.addAttribute("pendingWithdrawals",
                withdrawRepository.countByStatus(WithdrawStatus.PENDING.name()));
        model.addAttribute("recentUsers", userRepository.findTop5ByOrderByIdDesc());
        model.addAttribute("recentWithdrawals", withdrawRepository.findTop10ByOrderByDateRequestDesc());
        return "admin/dashboard";
    }
}
