package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.enums.WithdrawStatus;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.service.WithdrawServiceImpl;
import com.soa.spiriponzi.service.RenderService;
import com.soa.spiriponzi.service.CloudflareAnalyticsService;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.ActivationEmailResendResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AdRepository adRepository;
    private final ClaimRepository claimRepository;
    private final WithdrawRepository withdrawRepository;
    private final WithdrawServiceImpl withdrawService;
    private final RenderService renderService;
    private final CloudflareAnalyticsService cloudflareAnalyticsService;
    private final UserService userService;

    @GetMapping("/cloudflare/analytics")
    @ResponseBody
    public ResponseEntity<CloudflareAnalyticsService.AnalyticsSnapshot> cloudflareAnalytics() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(cloudflareAnalyticsService.getPageLoads());
    }

    @GetMapping("/render/services")
    @ResponseBody
    public ResponseEntity<List<RenderService.RenderServiceInfo>> renderServices() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(renderService.listServices());
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalBalance", userRepository.sumAnnualPoints());
        model.addAttribute("activeAds", adRepository.countByActiveTrueAndDeletedFalse());
        model.addAttribute("totalClaims", claimRepository.count());
        model.addAttribute("totalClaimAmount", claimRepository.sumClaimAmounts());
        model.addAttribute("pendingWithdrawals",
                withdrawRepository.countByStatus(WithdrawStatus.PENDING.name()));
        model.addAttribute("recentUsers", userRepository.findTop5ByOrderByIdDesc());
        List<User> usersAwaitingActivation =
                userRepository.findTop10ByEnabledFalseOrderByIdDesc();
        model.addAttribute("usersAwaitingActivation", usersAwaitingActivation);
        Set<Long> activationCooldownUserIds = new HashSet<>();
        for (User user : usersAwaitingActivation) {
            if (userService.isActivationEmailCoolingDown(user.getId())) {
                activationCooldownUserIds.add(user.getId());
            }
        }
        model.addAttribute("activationCooldownUserIds", activationCooldownUserIds);
        model.addAttribute("pendingWithdrawalRequests", withdrawRepository.findByStatusInOrderByDateRequestAsc(
                List.of(WithdrawStatus.PENDING.name()), PageRequest.of(0, 5)));
        return "admin/dashboard";
    }

    @PostMapping("/users/{id}/resend-activation")
    public String resendActivationEmail(@PathVariable long id, RedirectAttributes flash) {
        ActivationEmailResendResult result = userService.resendActivationEmailForAdmin(id);
        switch (result) {
            case SENT -> flash.addFlashAttribute("message", "Email di attivazione inviata.");
            case COOLDOWN -> flash.addFlashAttribute("error",
                    "Un'email è stata inviata da meno di un minuto. Attendi prima di riprovare.");
            case ALREADY_ACTIVATED -> flash.addFlashAttribute("error",
                    "Questo account è già stato attivato.");
        }
        return "redirect:/admin";
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
