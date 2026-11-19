package com.soa.spiriponzi.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WithdrawServiceImplTest {
    private WithdrawRepository withdrawals;
    private UserRepository users;
    private UserStatsRepository balances;
    private WithdrawServiceImpl service;
    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        withdrawals = mock(WithdrawRepository.class);
        users = mock(UserRepository.class);
        balances = mock(UserStatsRepository.class);
        service = new WithdrawServiceImpl(withdrawals, users, balances);
        stats = UserStats.builder().annualPoints(6100).build();
        user = User.builder().email("user@example.com").userStats(stats).build();
    }

    private void stubBalance() {
        when(users.findByEmail(user.getEmail())).thenReturn(user);
        when(balances.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
    }

    private WithdrawDto request(double amount, String method, String destination) {
        return WithdrawDto.builder().amount(amount).currency(method).destination(destination).build();
    }

    @Test
    void reservesPointsAndSnapshotsFeesAndRoundedValue() {
        stubBalance();
        service.startRequest(user.getEmail(), request(3059, "PAYPAL", " pay@example.com "));
        ArgumentCaptor<WithdrawRequest> saved = ArgumentCaptor.forClass(WithdrawRequest.class);
        verify(withdrawals).save(saved.capture());
        assertEquals(3041, stats.getAnnualPoints());
        assertEquals(new BigDecimal("0.50"), saved.getValue().getGrossEur());
        assertEquals(new BigDecimal("0.10"), saved.getValue().getFeeEur());
        assertEquals("pay@example.com", saved.getValue().getDestination());
        assertEquals("PENDING", saved.getValue().getStatus());
    }

    @Test
    void acceptsValidMainnetAddresses() {
        stubBalance();
        service.startRequest(user.getEmail(), request(3000, "BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
        service.startRequest(user.getEmail(), request(3000, "ETH", "0xde709f2102306220921060314715629080e2fb77"));
        assertEquals(100, stats.getAnnualPoints());
    }

    @Test
    void validatesEthereumChecksumsAndRejectsBurnAddress() {
        stubBalance();
        service.startRequest(user.getEmail(), request(3000, "ETH", "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE"));
        assertThrows(ResponseStatusException.class, () -> service.startRequest(user.getEmail(),
                request(3000, "ETH", "0x5AEDA56215b167893e80B4fE645BA6d5Bab767De")));
        assertThrows(ResponseStatusException.class, () -> service.startRequest(user.getEmail(),
                request(3000, "ETH", "0x0000000000000000000000000000000000000000")));
    }

    @Test
    void rejectsInvalidInputsAndInsufficientPointsWithoutReserving() {
        for (WithdrawDto dto : new WithdrawDto[] {
                request(2999, "PAYPAL", "user@example.com"),
                request(3000.5, "PAYPAL", "user@example.com"),
                request(Double.NaN, "PAYPAL", "user@example.com"),
                request(3000, "PAYPAL", "not-an-email"),
                request(3000, "BTC", "not-a-bitcoin-address"),
                request(3000, "BTC", "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"),
                request(3000, "ETH", "0x1234"),
                request(3000, "OTHER", "user@example.com")
        }) {
            assertThrows(ResponseStatusException.class, () -> service.startRequest(user.getEmail(), dto));
        }
        stubBalance();
        assertThrows(ResponseStatusException.class,
                () -> service.startRequest(user.getEmail(), request(6101, "PAYPAL", "user@example.com")));
        assertEquals(6100, stats.getAnnualPoints());
        verify(withdrawals, never()).save(any());
    }

    @Test
    void reservationsPreventSpendingTheSamePointsTwice() {
        stubBalance();
        service.startRequest(user.getEmail(), request(4000, "PAYPAL", "user@example.com"));
        assertThrows(ResponseStatusException.class,
                () -> service.startRequest(user.getEmail(), request(3000, "PAYPAL", "user@example.com")));
        assertEquals(2100, stats.getAnnualPoints());
        verify(withdrawals, times(1)).save(any());
    }

    private WithdrawRequest pending() {
        return WithdrawRequest.builder().id(1L).user(user).amount(3000).status("PENDING")
                .currency("PAYPAL").destination("pay@example.com")
                .grossEur(new BigDecimal("0.50")).feeEur(new BigDecimal("0.10")).build();
    }

    @Test
    void negativeBalanceAfterReversalBlocksApprovalUntilReviewed() {
        WithdrawRequest withdrawal = pending();
        stats.setAnnualPoints(-1);
        when(withdrawals.findWithLockById(1L)).thenReturn(Optional.of(withdrawal));
        when(balances.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        assertThrows(ResponseStatusException.class, () -> service.approve(1L));
        assertEquals("PENDING", withdrawal.getStatus());
        service.reject(1L);
        assertEquals(2999, stats.getAnnualPoints());
        assertEquals("REJECTED", withdrawal.getStatus());
        assertThrows(ResponseStatusException.class, () -> service.reject(1L));
    }

    @Test
    void approvesAndRecordsOnlyValidPayPalPaymentOnce() {
        WithdrawRequest withdrawal = pending();
        when(withdrawals.findWithLockById(1L)).thenReturn(Optional.of(withdrawal));
        when(balances.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        service.approve(1L);
        assertEquals("APPROVED", withdrawal.getStatus());
        assertThrows(ResponseStatusException.class, () -> service.approve(1L));
        assertThrows(ResponseStatusException.class, () -> service.complete(1L, "", new BigDecimal("0.40")));
        assertThrows(ResponseStatusException.class, () -> service.complete(1L, "PAY-123", new BigDecimal("0.50")));
        service.complete(1L, "PAY-123", new BigDecimal("0.40"));
        assertEquals("COMPLETED", withdrawal.getStatus());
        assertEquals(1, stats.getWithdrawals());
        assertThrows(ResponseStatusException.class, () -> service.complete(1L, "PAY-123", new BigDecimal("0.40")));
        assertThrows(ResponseStatusException.class, () -> service.reject(1L));
    }

    @Test
    void alreadySentPaymentCanBeRecordedAfterAReversal() {
        WithdrawRequest withdrawal = pending();
        withdrawal.setStatus("APPROVED");
        stats.setAnnualPoints(-5);
        when(withdrawals.findWithLockById(1L)).thenReturn(Optional.of(withdrawal));
        when(balances.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        service.complete(1L, "PAY-123", new BigDecimal("0.40"));
        assertEquals("COMPLETED", withdrawal.getStatus());
        assertEquals(-5, stats.getAnnualPoints());
    }

    @Test
    void approvedButUnpaidRequestCanBeCancelledOnlyOnce() {
        WithdrawRequest withdrawal = pending();
        withdrawal.setStatus("APPROVED");
        when(withdrawals.findWithLockById(1L)).thenReturn(Optional.of(withdrawal));
        when(balances.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));
        service.reject(1L);
        assertEquals("REJECTED", withdrawal.getStatus());
        assertEquals(9100, stats.getAnnualPoints());
        assertThrows(ResponseStatusException.class, () -> service.reject(1L));
    }

    @Test
    void doesNotModifyLegacyRequestsWithUnreservedPoints() {
        WithdrawRequest old = WithdrawRequest.builder().id(1L).user(user).amount(3000).status("PENDING").build();
        when(withdrawals.findWithLockById(1L)).thenReturn(Optional.of(old));
        assertThrows(ResponseStatusException.class, () -> service.approve(1L));
        assertThrows(ResponseStatusException.class, () -> service.reject(1L));
        assertEquals(6100, stats.getAnnualPoints());
        service.archiveLegacy(1L);
        assertEquals("REJECTED", old.getStatus());
        assertEquals(6100, stats.getAnnualPoints());
        assertThrows(ResponseStatusException.class, () -> service.archiveLegacy(1L));
    }
}
