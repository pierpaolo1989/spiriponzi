package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.exception.ClaimException;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
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
    private ClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        claimService = new ClaimServiceImpl(claimRepository, userRepository, userStatsRepository);
    }

    @Test
    void gameClaimCooldownStartsAtSixtySecondsWhenFirstVisited() {
        User user = User.builder().id(1L).email("player@example.com").build();
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);

        long remaining = claimService.getGameClaimCooldownSeconds(user.getEmail());

        assertEquals(60, remaining);
        assertNotNull(user.getUserStats().getLastGameClaimAt());
        verify(userStatsRepository).save(user.getUserStats());
        verify(userRepository).save(user);
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

        claimService.startClaim(email);

        Claim claim = captureSavedClaim();
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
    void gameClaimAwardsOneHundredCoinsAndUpdatesStatsAfterCooldown() {
        String email = "player@example.com";
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder().id(1L).email(email).build();
        UserStats stats = UserStats.builder()
                .annualPoints(300)
                .monthlyPoints(25)
                .numberOfClaims(4)
                .withdrawals(1)
                .lastRequest(now.minusMinutes(2))
                .lastGameClaimAt(now.minusSeconds(61))
                .build();
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));
        when(userRepository.findByEmail(email)).thenReturn(user);

        var result = claimService.startGameClaim(email);

        assertEquals(400, result.getAnnualPoints());
        assertEquals(125, result.getMonthlyPoints());
        assertEquals(5, result.getNumberOfClaims());
        assertEquals(1, result.getWithdrawals());
        assertEquals(100, captureSavedClaim().getAmount());
        assertEquals(60, claimService.getGameClaimCooldownSeconds(email));
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

        assertEquals(100, result.getMonthlyPoints());
        assertEquals(400, result.getAnnualPoints());
    }

    @Test
    void gameClaimIsRejectedBeforeSixtySecondsHavePassed() {
        String email = "player@example.com";
        UserStats stats = UserStats.builder().lastGameClaimAt(LocalDateTime.now().minusSeconds(30)).build();
        when(userStatsRepository.findWithLockByUserEmail(email)).thenReturn(Optional.of(stats));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> claimService.startGameClaim(email)
        );

        assertEquals(429, exception.getStatusCode().value());
        verify(claimRepository, never()).save(any(Claim.class));
        verify(userRepository, never()).findByEmail(email);
    }

    private Claim captureSavedClaim() {
        var captor = org.mockito.ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        return captor.getValue();
    }
}
