package com.soa.spiriponzi.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import com.soa.spiriponzi.model.PromoCode;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.PromoCodeRedemptionRepository;
import com.soa.spiriponzi.repository.PromoCodeRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeRedemptionRepository redemptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserStatsRepository userStatsRepository;

    private PromoCodeService service;

    @BeforeEach
    void setUp() {
        service = new PromoCodeService(
                promoCodeRepository, redemptionRepository, userRepository, userStatsRepository, CLOCK);
    }

    @Test
    void createsCodeWithRequestedRewardLimitAndExpiry() {
        when(promoCodeRepository.existsByCode(anyString())).thenReturn(false);
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = service.createCode(250, 12, LocalDate.of(2026, 12, 31));

        assertEquals(250, result.getPoints());
        assertEquals(12, result.getMaxRedemptions());
        assertEquals(0, result.getRedemptionsUsed());
        assertEquals(LocalDate.of(2026, 12, 31), result.getExpiresOn());
        assertEquals("SP-", result.getCode().substring(0, 3));
        verify(promoCodeRepository).save(result);
    }

    @Test
    void redemptionCreditsPointsAndConsumesOneUse() {
        PromoCode promoCode = promoCode(250, 5, 0);
        User user = new User();
        user.setId(7L);
        UserStats stats = new UserStats();
        stats.setAnnualPoints(40);
        when(promoCodeRepository.findByCodeForUpdate("SP-TEST")).thenReturn(Optional.of(promoCode));
        when(userRepository.findByEmail("user@example.com")).thenReturn(user);
        when(redemptionRepository.existsByPromoCode_IdAndUser_Id(3L, 7L)).thenReturn(false);
        when(userStatsRepository.findWithLockByUserEmail("user@example.com")).thenReturn(Optional.of(stats));

        int awardedPoints = service.redeemCode(" sp-test ", "user@example.com");

        assertEquals(250, awardedPoints);
        assertEquals(290, stats.getAnnualPoints());
        assertEquals(1, promoCode.getRedemptionsUsed());
        verify(userStatsRepository).save(stats);
        verify(promoCodeRepository).save(promoCode);
        verify(redemptionRepository).save(any());
    }

    @Test
    void rejectsSecondRedemptionByTheSameUser() {
        PromoCode promoCode = promoCode(250, 5, 0);
        User user = new User();
        user.setId(7L);
        when(promoCodeRepository.findByCodeForUpdate("SP-TEST")).thenReturn(Optional.of(promoCode));
        when(userRepository.findByEmail("user@example.com")).thenReturn(user);
        when(redemptionRepository.existsByPromoCode_IdAndUser_Id(3L, 7L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.redeemCode("SP-TEST", "user@example.com"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(userStatsRepository);
    }

    @Test
    void rejectsExpiredOrExhaustedCodes() {
        PromoCode expired = promoCode(250, 5, 0);
        expired.setExpiresOn(LocalDate.of(2026, 11, 25));
        when(promoCodeRepository.findByCodeForUpdate("SP-TEST")).thenReturn(Optional.of(expired));
        ResponseStatusException expiredException = assertThrows(ResponseStatusException.class,
                () -> service.redeemCode("SP-TEST", "user@example.com"));
        assertEquals(HttpStatus.GONE, expiredException.getStatusCode());

        PromoCode exhausted = promoCode(250, 1, 1);
        when(promoCodeRepository.findByCodeForUpdate("SP-TEST")).thenReturn(Optional.of(exhausted));
        ResponseStatusException exhaustedException = assertThrows(ResponseStatusException.class,
                () -> service.redeemCode("SP-TEST", "user@example.com"));
        assertEquals(HttpStatus.GONE, exhaustedException.getStatusCode());
        verifyNoInteractions(userRepository, redemptionRepository, userStatsRepository);
    }

    private PromoCode promoCode(int points, int maxRedemptions, int redemptionsUsed) {
        PromoCode promoCode = new PromoCode();
        promoCode.setId(3L);
        promoCode.setCode("SP-TEST");
        promoCode.setPoints(points);
        promoCode.setMaxRedemptions(maxRedemptions);
        promoCode.setRedemptionsUsed(redemptionsUsed);
        promoCode.setExpiresOn(LocalDate.of(2026, 11, 26));
        return promoCode;
    }
}
