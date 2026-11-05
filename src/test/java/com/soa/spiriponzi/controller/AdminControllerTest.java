package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

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
    @InjectMocks
    private AdminController adminController;

    @Test
    void dashboardLoadsApplicationMetricsAndRecentRecords() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<User> recentUsers = List.of(new User());
        List<WithdrawRequest> recentWithdrawals = List.of(new WithdrawRequest());
        when(userRepository.count()).thenReturn(12L);
        when(userRepository.sumBalances()).thenReturn(42.5);
        when(adRepository.countByActiveTrueAndDeletedFalse()).thenReturn(3L);
        when(claimRepository.count()).thenReturn(25L);
        when(claimRepository.sumClaimAmounts()).thenReturn(15.75);
        when(withdrawRepository.countByStatus("PENDING")).thenReturn(2L);
        when(userRepository.findTop5ByOrderByIdDesc()).thenReturn(recentUsers);
        when(withdrawRepository.findTop10ByOrderByDateRequestDesc()).thenReturn(recentWithdrawals);

        String view = adminController.dashboard(model);

        assertEquals("admin/dashboard", view);
        assertEquals(12L, model.getAttribute("totalUsers"));
        assertEquals(42.5, model.getAttribute("totalBalance"));
        assertEquals(3L, model.getAttribute("activeAds"));
        assertEquals(25L, model.getAttribute("totalClaims"));
        assertEquals(15.75, model.getAttribute("totalClaimAmount"));
        assertEquals(2L, model.getAttribute("pendingWithdrawals"));
        assertEquals(recentUsers, model.getAttribute("recentUsers"));
        assertEquals(recentWithdrawals, model.getAttribute("recentWithdrawals"));
        verify(withdrawRepository).countByStatus("PENDING");
    }
}
