package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.enums.WithdrawStatus;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.service.WithdrawServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AdRepository adRepository;
    private final ClaimRepository claimRepository;
    private final WithdrawRepository withdrawRepository;
    private final WithdrawServiceImpl withdrawService;

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

    @GetMapping("/withdrawals")
    public String withdrawals(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("withdrawals", withdrawService.pendingRequests(Math.max(page, 0)));
        return "admin/withdrawals";
    }

    @PostMapping("/withdrawals/{id}/approve")
    public String approveWithdrawal(@PathVariable long id, RedirectAttributes flash) {
        try {
            withdrawService.approve(id);
            flash.addFlashAttribute("message", "Richiesta approvata. Il pagamento deve essere inviato manualmente.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/admin/withdrawals";
    }

    @PostMapping("/withdrawals/{id}/reject")
    public String rejectWithdrawal(@PathVariable long id, RedirectAttributes flash) {
        try {
            withdrawService.reject(id);
            flash.addFlashAttribute("message", "Richiesta rifiutata e punti restituiti.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/admin/withdrawals";
    }

    @PostMapping("/withdrawals/{id}/archive-legacy")
    public String archiveLegacyWithdrawal(@PathVariable long id, RedirectAttributes flash) {
        try {
            withdrawService.archiveLegacy(id);
            flash.addFlashAttribute("message", "Richiesta precedente archiviata senza modificare il saldo.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/admin/withdrawals";
    }

    @PostMapping("/withdrawals/{id}/complete")
    public String completeWithdrawal(@PathVariable long id, @RequestParam String reference,
                                     @RequestParam BigDecimal paidAmount, RedirectAttributes flash) {
        try {
            withdrawService.complete(id, reference, paidAmount);
            flash.addFlashAttribute("message", "Pagamento registrato.");
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/admin/withdrawals";
    }
}
