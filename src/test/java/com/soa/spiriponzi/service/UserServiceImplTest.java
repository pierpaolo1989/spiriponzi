package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.PasswordResetToken;
import com.soa.spiriponzi.model.Role;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.PasswordResetTokenRepository;
import com.soa.spiriponzi.repository.RoleRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AdRepository adRepository;
    private UserAdRepository userAdRepository;
    private EmailService emailService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        adRepository = mock(AdRepository.class);
        userAdRepository = mock(UserAdRepository.class);
        emailService = mock(EmailService.class);
        userService = new UserServiceImpl(
                userRepository,
                passwordResetTokenRepository,
                userStatsRepository,
                roleRepository,
                passwordEncoder,
                emailService,
                adRepository,
                userAdRepository
        );
        ReflectionTestUtils.setField(userService, "publicBaseUrl", "https://www.spiriponzi.cloud");
    }

    @Test
    void recoverPasswordCreatesHashedExpiringTokenAndSendsResetEmail() throws Exception {
        User user = User.builder().id(21L).email("person@example.com").build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        RecoverPasswordDto request =
                RecoverPasswordDto.builder()
                        .email(user.getEmail()).confirmEmail(user.getEmail()).build();

        userService.recoverPassword(request);

        org.mockito.ArgumentCaptor<PasswordResetToken> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).deleteAllByUserId(user.getId());
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken storedToken = tokenCaptor.getValue();
        assertTrue(storedToken.getTokenHash().matches("[a-f0-9]{64}"));
        assertTrue(storedToken.getExpiresAt().isAfter(java.time.LocalDateTime.now()));

        org.mockito.ArgumentCaptor<String> urlCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(user.getEmail()), urlCaptor.capture());
        assertTrue(urlCaptor.getValue().startsWith("https://www.spiriponzi.cloud/reset-password?token="));
        assertFalse(urlCaptor.getValue().contains(storedToken.getTokenHash()));
    }

    @Test
    void passwordResetRejectsUnknownToken() {
        when(passwordResetTokenRepository.findWithLockByTokenHash(any())).thenReturn(Optional.empty());

        assertFalse(userService.resetPassword("invalid", "new-password"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void passwordResetChangesPasswordAndConsumesToken() throws Exception {
        String rawToken = "single-use-reset-token";
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        User user = User.builder().id(34L).email("person@example.com").password("old").build();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findWithLockByTokenHash(tokenHash))
                .thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        assertTrue(userService.resetPassword(rawToken, "new-password"));

        assertEquals("encoded-new-password", user.getPassword());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).deleteAllByUserId(user.getId());
    }

    @Test
    void passwordResetRequestForUnknownEmailDoesNotSendEmail() throws Exception {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(null);

        userService.recoverPassword(RecoverPasswordDto.builder()
                .email("missing@example.com").confirmEmail("missing@example.com").build());

        verify(emailService, never()).sendPasswordResetEmail(any(), any());
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void registeringWithReferralCodeRewardsBothUsers() {
        User referrer = User.builder().id(10L).email("referrer@example.com").build();
        UserStats referrerStats = UserStats.builder().annualPoints(250).monthlyPoints(100).build();
        when(userRepository.findByReferralCode("REFERRAL123")).thenReturn(Optional.of(referrer));
        when(userRepository.existsByReferralCode(any())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(new Role());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(userStatsRepository.findWithLockByUserEmail(referrer.getEmail())).thenReturn(Optional.of(referrerStats));

        UserDto registration = new UserDto();
        registration.setEmail("new-user@example.com");
        registration.setPassword("password");
        registration.setReferralCode(" referral123 ");

        userService.saveUser(registration);

        assertEquals(1250, referrerStats.getAnnualPoints());
        assertEquals(1100, referrerStats.getMonthlyPoints());
        verify(userStatsRepository).save(referrerStats);
        org.mockito.ArgumentCaptor<UserStats> newStatsCaptor =
                org.mockito.ArgumentCaptor.forClass(UserStats.class);
        verify(userStatsRepository, org.mockito.Mockito.times(2)).save(newStatsCaptor.capture());
        UserStats newUserStats = newStatsCaptor.getAllValues().get(0);
        assertEquals(1000, newUserStats.getAnnualPoints());
        assertEquals(1000, newUserStats.getMonthlyPoints());
        assertEquals(0, newUserStats.getNumberOfClaims());

        org.mockito.ArgumentCaptor<User> userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User newUser = userCaptor.getValue();
        assertEquals(referrer, newUser.getReferredBy());
        assertNotNull(newUser.getReferralCode());
        assertTrue(newUser.getReferralCode().matches("[A-F0-9]{16}"));
    }

    @Test
    void getOrCreateReferralCodePersistsCodeForExistingUsers() {
        User user = User.builder().id(42L).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.existsByReferralCode(any())).thenReturn(false);

        String referralCode = userService.getOrCreateReferralCode(42L);

        assertTrue(referralCode.matches("[A-F0-9]{16}"));
        assertEquals(referralCode, user.getReferralCode());
        verify(userRepository).save(user);
    }

    @Test
    void saveUserRejectsUnknownReferralCodeWithoutSavingAccount() {
        UserDto registration = new UserDto();
        registration.setReferralCode("UNKNOWN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.saveUser(registration)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void surfedAdWithNoPreviousClaimUpdatesAnnualAndMonthlyPoints() {
        User user = User.builder().id(4L).email("player@example.com").build();
        UserStats stats = UserStats.builder()
                .annualPoints(50)
                .monthlyPoints(50)
                .numberOfClaims(0)
                .lastRequest(null)
                .build();
        Ad ad = Ad.builder().id(5L).active(true).reward(20).build();
        when(adRepository.findById(5L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));

        userService.createUserAd(user, 5L);

        assertEquals(70, stats.getAnnualPoints());
        assertEquals(70, stats.getMonthlyPoints());
        verify(userStatsRepository).save(stats);
        verify(userAdRepository).save(any(com.soa.spiriponzi.model.UserAd.class));
    }
}
