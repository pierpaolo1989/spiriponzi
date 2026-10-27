package com.soa.spiriponzi.service;

import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class ADServiceImplTest {

    private AdRepository adRepository;
    private UserStatsRepository userStatsRepository;
    private ADServiceImpl adService;

    @BeforeEach
    void setUp() {
        adRepository = mock(AdRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        adService = new ADServiceImpl(mock(UserAdRepository.class), adRepository, userStatsRepository);
    }

    @Test
    void updateBudgetSavesAmountWithinRewardAndBalanceLimits() {
        User owner = userWithCoins(1L, 0, 100);
        Ad ad = Ad.builder()
                .id(10L)
                .reward(10)
                .proposer(owner)
                .build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(owner.getEmail())).thenReturn(Optional.of(owner.getUserStats()));

        assertEquals(80, adService.updateBudget(owner, 10L, 20));

        assertEquals(20, ad.getBudget());
        assertEquals(80, owner.getUserStats().getAnnualPoints());
        verify(userStatsRepository).save(owner.getUserStats());
        verify(adRepository).save(ad);
    }

    @Test
    void updateBudgetRefundsTheDifferenceWhenBudgetIsReduced() {
        User owner = userWithCoins(1L, 0, 80);
        Ad ad = Ad.builder().id(10L).reward(10).budget(20).proposer(owner).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(owner.getEmail())).thenReturn(Optional.of(owner.getUserStats()));

        assertEquals(90, adService.updateBudget(owner, 10L, 10));

        assertEquals(10, ad.getBudget());
        assertEquals(90, owner.getUserStats().getAnnualPoints());
        verify(userStatsRepository).save(owner.getUserStats());
    }

    @Test
    void updateBudgetRejectsAmountBelowReward() {
        User owner = userWithCoins(1L, 0, 100);
        Ad ad = Ad.builder().id(10L).reward(10).proposer(owner).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adService.updateBudget(owner, 10L, 9)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(userStatsRepository, never()).save(owner.getUserStats());
    }

    @Test
    void updateBudgetRejectsAmountThatIsNotMultipleOfReward() {
        User owner = userWithCoins(1L, 0, 100);
        Ad ad = Ad.builder().id(10L).reward(10).proposer(owner).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adService.updateBudget(owner, 10L, 11)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void updateBudgetRejectsAmountAboveAvailableBalance() {
        User owner = userWithCoins(1L, 0, 100);
        Ad ad = Ad.builder().id(10L).reward(5).proposer(owner).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(owner.getEmail())).thenReturn(Optional.of(owner.getUserStats()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adService.updateBudget(owner, 10L, 101)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(userStatsRepository, never()).save(owner.getUserStats());
        verify(adRepository, never()).save(ad);
    }

    @Test
    void updateBudgetRejectsAdOwnedByAnotherUser() {
        User owner = userWithCoins(1L, 100, 100);
        User anotherUser = userWithCoins(2L, 100, 100);
        Ad ad = Ad.builder().id(10L).reward(5).proposer(owner).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(owner.getEmail())).thenReturn(Optional.of(owner.getUserStats()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adService.updateBudget(anotherUser, 10L, 10)
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void toggleActivePublishesAndPausesAd() {
        User owner = userWithCoins(1L, 0, 100);
        Ad ad = Ad.builder().id(10L).proposer(owner).active(false).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));

        assertTrue(adService.toggleActive(owner, 10L));
        assertTrue(ad.isActive());
        verify(adRepository).save(ad);

        assertFalse(adService.toggleActive(owner, 10L));
        assertFalse(ad.isActive());
    }

    @Test
    void toggleActiveRejectsAdOwnedByAnotherUser() {
        User owner = userWithCoins(1L, 0, 100);
        User anotherUser = userWithCoins(2L, 0, 100);
        Ad ad = Ad.builder().id(10L).proposer(owner).active(false).build();
        when(adRepository.findById(10L)).thenReturn(Optional.of(ad));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adService.toggleActive(anotherUser, 10L)
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    private User userWithCoins(Long id, double balance, int annualPoints) {
        UserStats stats = UserStats.builder().annualPoints(annualPoints).build();
        return User.builder().id(id).balance(balance).userStats(stats).build();
    }
}
