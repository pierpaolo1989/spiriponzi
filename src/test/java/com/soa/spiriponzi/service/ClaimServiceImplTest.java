package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.exception.ClaimException;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimServiceImplTest {

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private WithdrawRepository withdrawRepository;
    private ClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        withdrawRepository = mock(WithdrawRepository.class);
        claimService = new ClaimServiceImpl(claimRepository, userRepository, userStatsRepository, withdrawRepository);
        org.mockito.Mockito.when(claimRepository.save(any(Claim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void gameClaimCooldownStartsAtOneHourWhenFirstVisited() {
        User user = User.builder().id(1L).email("player@example.com").build();
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(userRepository.findWithLockById(user.getId())).thenReturn(Optional.of(user));

        long remaining = claimService.getGameClaimCooldownSeconds(user.getEmail());

        assertEquals(3600, remaining);
        assertNotNull(user.getUserStats().getLastGameClaimAt());
        verify(userStatsRepository).save(user.getUserStats());
        verify(userRepository).save(user);
    }

    @Test
    void claimCooldownReturnsSecondsFromServerSideTimestamp() {
        String email = "player@example.com";
        UserStats stats = UserStats.builder().lastRequest(LocalDateTime.now().minusMinutes(30)).build();
        when(userStatsRepository.findByUserEmail(email)).thenReturn(Optional.of(stats));

        long remaining = claimService.getClaimCooldownSeconds(email);

        assertEquals(1800, remaining);
    }

    @Test
    void claimCooldownIsZeroWhenNoRecentClaimExists() {
        String email = "player@example.com";
        UserStats stats = UserStats.builder().lastRequest(LocalDateTime.now().minusHours(2)).build();
        when(userStatsRepository.findByUserEmail(email)).thenReturn(Optional.of(stats));

        assertEquals(0, claimService.getClaimCooldownSeconds(email));
    }

    @Test
    void firstClaimCreditsPointsAndIncrementsClaimCountWhenLastRequestIsNull() {
        String email = "player@example.com";
        User user = User.builder().id(1L).email(email).build();
        UserStats stats = UserStats.builder()
                .annualPoints(1000)
                .monthlyPoints(1000)
                .numberOfClaims(0)
                .build();
        when(userRepository.findByEmail(email)).thenReturn(user);
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));

        var result = claimService.startClaim(email);

        Claim claim = captureSavedClaim();
        assertEquals(claim.getId(), result.getId());
        assertEquals(claim.getAmount(), result.getAmount());
        assertEquals(claim.getDateRequest(), result.getDate());
        assertEquals(user, claim.getUser());
        assertEquals(1000 + claim.getAmount(), stats.getAnnualPoints());
        assertEquals(1000 + claim.getAmount(), stats.getMonthlyPoints());
        assertEquals(1, stats.getNumberOfClaims());
        assertEquals(claim.getDateRequest(), stats.getLastRequest());
        verify(userStatsRepository).save(stats);
    }

    @Test
    void claimDuringCooldownDoesNotPersistAClaim() {
        String email = "player@example.com";
        UserStats stats = UserStats.builder().lastRequest(LocalDateTime.now().minusMinutes(30)).build();
        when(userRepository.findByEmail(email)).thenReturn(User.builder().email(email).build());
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));

        assertThrows(ClaimException.class, () -> claimService.startClaim(email));

        verify(claimRepository, never()).save(any(Claim.class));
        verify(userStatsRepository, never()).save(stats);
    }

    @Test
    void gameClaimAwardsFiftyCoinsAndUpdatesStatsAfterCooldown() {
        String email = "player@example.com";
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder().id(1L).email(email).build();
        UserStats stats = UserStats.builder()
                .annualPoints(300)
                .monthlyPoints(25)
                .numberOfClaims(4)
                .withdrawals(1)
                .lastRequest(now.minusMinutes(2))
                .lastGameClaimAt(now.minusHours(1))
                .build();
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));
        when(userRepository.findByEmail(email)).thenReturn(user);
        when(withdrawRepository.countByUserEmailAndStatusIn(email, java.util.List.of("APPROVED", "COMPLETED")))
                .thenReturn(1L);

        var result = claimService.startGameClaim(email);

        assertEquals(350, result.getAnnualPoints());
        assertEquals(75, result.getMonthlyPoints());
        assertEquals(5, result.getNumberOfClaims());
        assertEquals(1, result.getWithdrawals());
        assertEquals(50, captureSavedClaim().getAmount());
        assertEquals(3600, claimService.getGameClaimCooldownSeconds(email));
        assertThrows(ResponseStatusException.class, () -> claimService.startGameClaim(email));
        verify(userStatsRepository).save(stats);
    }

    @Test
    void gameClaimResetsMonthlyPointsForANewMonth() {
        String email = "player@example.com";
        User user = User.builder().id(1L).email(email).build();
        UserStats stats = UserStats.builder()
                .annualPoints(300)
                .monthlyPoints(25)
                .numberOfClaims(4)
                .lastRequest(LocalDateTime.now().minusMonths(2))
                .lastGameClaimAt(LocalDateTime.now().minusMonths(2))
                .build();
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));
        when(userRepository.findByEmail(email)).thenReturn(user);

        var result = claimService.startGameClaim(email);

        assertEquals(50, result.getMonthlyPoints());
        assertEquals(350, result.getAnnualPoints());
    }

    @Test
    void gameClaimIsRejectedJustBeforeOneHourHasPassed() {
        String email = "player@example.com";
        UserStats stats = UserStats.builder().lastGameClaimAt(LocalDateTime.now().minusHours(1).plusMinutes(1)).build();
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> claimService.startGameClaim(email)
        );

        assertEquals(429, exception.getStatusCode().value());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userRepository, never()).findByEmail(email);
    }

    @Test
    void gameClaimWithoutPriorStatusStartsCooldownAndRejectsClaim() {
        String email = "player@example.com";
        UserStats stats = UserStats.builder().build();
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> claimService.startGameClaim(email));

        assertEquals(429, exception.getStatusCode().value());
        assertNotNull(stats.getLastGameClaimAt());
        verify(userStatsRepository).save(stats);
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void gameClaimRechecksStatsAfterLockingUser() {
        String email = "player@example.com";
        User user = User.builder().id(1L).email(email).build();
        UserStats stats = UserStats.builder().lastGameClaimAt(LocalDateTime.now()).build();
        when(userStatsRepository.findWithLockByUserEmail(email))
                .thenReturn(Optional.empty(), Optional.of(stats));
        when(userRepository.findByEmail(email)).thenReturn(user);
        when(userRepository.findWithLockById(user.getId())).thenReturn(Optional.of(user));

        assertEquals(3600, claimService.getGameClaimCooldownSeconds(email));
        verify(userStatsRepository, never()).save(any(UserStats.class));
        verify(userRepository, never()).save(any(User.class));
    }

    private Claim captureSavedClaim() {
        var captor = org.mockito.ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        return captor.getValue();
    }
}
