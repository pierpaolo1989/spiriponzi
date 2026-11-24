package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.service.CloudflareAnalyticsService;
import com.soa.spiriponzi.service.RenderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdRepository adRepository;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private WithdrawRepository withdrawRepository;
    @Mock
    private RenderService renderService;
    @Mock
    private CloudflareAnalyticsService cloudflareAnalyticsService;
    @InjectMocks
    private AdminController adminController;

    @Test
    void dashboardLoadsApplicationMetricsAndRecentRecords() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<User> recentUsers = List.of(new User());
        var pendingWithdrawals = new PageImpl<>(List.of(new WithdrawRequest()));
        when(userRepository.count()).thenReturn(12L);
        when(userRepository.sumAnnualPoints()).thenReturn(42L);
        when(adRepository.countByActiveTrueAndDeletedFalse()).thenReturn(3L);
        when(claimRepository.count()).thenReturn(25L);
        when(claimRepository.sumClaimAmounts()).thenReturn(15.75);
        when(withdrawRepository.countByStatus("PENDING")).thenReturn(2L);
        when(userRepository.findTop5ByOrderByIdDesc()).thenReturn(recentUsers);
        when(withdrawRepository.findByStatusInOrderByDateRequestAsc(
                List.of("PENDING"), PageRequest.of(0, 5))).thenReturn(pendingWithdrawals);

        String view = adminController.dashboard(model);

        assertEquals("admin/dashboard", view);
        assertEquals(12L, model.getAttribute("totalUsers"));
        assertEquals(42L, model.getAttribute("totalBalance"));
        assertEquals(3L, model.getAttribute("activeAds"));
        assertEquals(25L, model.getAttribute("totalClaims"));
        assertEquals(15.75, model.getAttribute("totalClaimAmount"));
        assertEquals(2L, model.getAttribute("pendingWithdrawals"));
        assertEquals(recentUsers, model.getAttribute("recentUsers"));
        assertEquals(pendingWithdrawals, model.getAttribute("pendingWithdrawalRequests"));
        verify(withdrawRepository).countByStatus("PENDING");
    }
}
