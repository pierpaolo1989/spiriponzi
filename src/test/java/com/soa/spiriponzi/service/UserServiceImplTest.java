package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.EmailVerificationToken;
import com.soa.spiriponzi.model.PasswordResetToken;
import com.soa.spiriponzi.model.Role;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.EmailVerificationTokenRepository;
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
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
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
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        adRepository = mock(AdRepository.class);
        userAdRepository = mock(UserAdRepository.class);
        emailService = mock(EmailService.class);
        userService = new UserServiceImpl(
                userRepository,
                passwordResetTokenRepository,
                emailVerificationTokenRepository,
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
    void updateUserSavesAndClearsPaypalEmail() {
        User user = User.builder().email("person@example.com").build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        UpdateUserDto details = UpdateUserDto.builder().email(user.getEmail())
                .paypalEmail("pay@example.com").btcWallet("btc").ethWallet("eth").build();

        userService.updateUser(details);
        assertEquals("pay@example.com", user.getPaypalEmail());
        assertEquals("btc", user.getBtcWallet());
        assertEquals("eth", user.getEthWallet());

        details.setPaypalEmail("");
        userService.updateUser(details);
        org.junit.jupiter.api.Assertions.assertNull(user.getPaypalEmail());
        verify(userRepository, org.mockito.Mockito.times(2)).save(user);
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
    void registeringWithReferralCodeDefersRewardsUntilActivation() throws Exception {
        User referrer = User.builder().id(10L).email("referrer@example.com").build();
        UserStats referrerStats = UserStats.builder().annualPoints(250).monthlyPoints(100).build();
        when(userRepository.findByReferralCode("REFERRAL123")).thenReturn(Optional.of(referrer));
        when(userRepository.existsByReferralCode(any())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(new Role());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");

        UserDto registration = new UserDto();
        registration.setEmail("new-user@example.com");
        registration.setPassword("password");
        registration.setReferralCode(" referral123 ");

        userService.saveUser(registration);

        assertEquals(250, referrerStats.getAnnualPoints());
        assertEquals(100, referrerStats.getMonthlyPoints());
        verify(userStatsRepository, never()).save(referrerStats);
        org.mockito.ArgumentCaptor<UserStats> newStatsCaptor =
                org.mockito.ArgumentCaptor.forClass(UserStats.class);
        verify(userStatsRepository).save(newStatsCaptor.capture());
        UserStats newUserStats = newStatsCaptor.getValue();
        assertEquals(0, newUserStats.getAnnualPoints());
        assertEquals(0, newUserStats.getMonthlyPoints());
        assertEquals(0, newUserStats.getNumberOfClaims());

        org.mockito.ArgumentCaptor<User> userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User newUser = userCaptor.getValue();
        assertEquals(referrer, newUser.getReferredBy());
        assertFalse(newUser.isEnabled());
        assertNotNull(newUser.getReferralCode());
        assertTrue(newUser.getReferralCode().matches("[A-F0-9]{16}"));
        verify(emailService).sendActivationEmail(org.mockito.ArgumentMatchers.eq(newUser.getEmail()),
                org.mockito.ArgumentMatchers.contains("/activate?token="));
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void activationEnablesAccountAndRewardsBothUsersOnlyOnce() throws Exception {
        String rawToken = "single-use-activation-token";
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        User referrer = User.builder().id(10L).email("referrer@example.com").build();
        User newUser = User.builder().id(11L).email("new-user@example.com").enabled(false)
                .referredBy(referrer).build();
        UserStats newUserStats = UserStats.builder().annualPoints(0).monthlyPoints(0).build();
        UserStats referrerStats = UserStats.builder().annualPoints(250).monthlyPoints(100).build();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setTokenHash(tokenHash);
        token.setUser(newUser);
        token.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        when(emailVerificationTokenRepository.findWithLockByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userStatsRepository.findWithLockByUserEmail(newUser.getEmail())).thenReturn(Optional.of(newUserStats));
        when(userStatsRepository.findWithLockByUserEmail(referrer.getEmail())).thenReturn(Optional.of(referrerStats));

        assertTrue(userService.activateAccount(rawToken));

        assertTrue(newUser.isEnabled());
        assertEquals(1000, newUserStats.getAnnualPoints());
        assertEquals(1000, newUserStats.getMonthlyPoints());
        assertEquals(1250, referrerStats.getAnnualPoints());
        assertEquals(1100, referrerStats.getMonthlyPoints());
        verify(userRepository).save(newUser);
        verify(emailVerificationTokenRepository).delete(token);
        verify(userStatsRepository).save(newUserStats);
        verify(userStatsRepository).save(referrerStats);
    }

    @Test
    void activationRejectsExpiredTokenWithoutEnablingOrRewarding() throws Exception {
        String rawToken = "expired-activation-token";
        String tokenHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        User user = User.builder().id(11L).email("new-user@example.com").enabled(false).build();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        when(emailVerificationTokenRepository.findWithLockByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertFalse(userService.activateAccount(rawToken));

        assertFalse(user.isEnabled());
        verify(emailVerificationTokenRepository).delete(token);
        verify(userRepository, never()).save(any(User.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void googleRegistrationDefersRewardsUntilEmailActivation() throws Exception {
        User referrer = User.builder().id(10L).email("referrer@example.com").build();
        UserStats stats = UserStats.builder().annualPoints(10).monthlyPoints(20).build();
        UserStats registeredStats = UserStats.builder().annualPoints(0).monthlyPoints(0).build();
        when(userRepository.findByGoogleSubject("google-sub")).thenReturn(Optional.empty());
        when(userRepository.findByReferralCode("REF123")).thenReturn(Optional.of(referrer));
        when(userStatsRepository.findWithLockByUserEmail(referrer.getEmail())).thenReturn(Optional.of(stats));
        when(userStatsRepository.findWithLockByUserEmail("new@gmail.com")).thenReturn(Optional.of(registeredStats));
        when(roleRepository.findByName("ROLE_USER")).thenReturn(new Role());
        when(passwordEncoder.encode(any())).thenReturn("random-encoded-password");

        User registered = userService.signInWithGoogle("google-sub", "new@gmail.com", true, "ref123");

        assertEquals("google-sub", registered.getGoogleSubject());
        assertEquals("new@gmail.com", registered.getEmail());
        assertEquals("random-encoded-password", registered.getPassword());
        assertFalse(registered.isEnabled());
        assertEquals(0, registeredStats.getAnnualPoints());
        assertEquals(10, stats.getAnnualPoints());
        assertEquals(20, stats.getMonthlyPoints());
        verify(userRepository).save(registered);
        verify(emailService).sendActivationEmail(org.mockito.ArgumentMatchers.eq(registered.getEmail()),
                org.mockito.ArgumentMatchers.contains("/activate?token="));
    }

    @Test
    void googleSignInUsesSubjectAndNeverLinksExistingEmail() {
        User existing = User.builder().email("original@gmail.com").googleSubject("google-sub").build();
        when(userRepository.findByGoogleSubject("google-sub")).thenReturn(Optional.of(existing));
        assertEquals(existing, userService.signInWithGoogle("google-sub", "changed@gmail.com", false, null));
        when(userRepository.findByGoogleSubject("new-sub")).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("original@gmail.com")).thenReturn(true);
        assertThrows(ResponseStatusException.class,
                () -> userService.signInWithGoogle("new-sub", "original@gmail.com", true, ""));
        assertThrows(ResponseStatusException.class,
                () -> userService.signInWithGoogle("new-sub", "other@gmail.com", false, null));
        verify(userRepository, never()).save(any(User.class));
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
        when(adRepository.findWithLockById(5L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(user.getEmail())).thenReturn(Optional.of(stats));

        userService.createUserAd(user, 5L);

        assertEquals(70, stats.getAnnualPoints());
        assertEquals(70, stats.getMonthlyPoints());
        verify(userStatsRepository).save(stats);
        verify(userAdRepository).save(any(com.soa.spiriponzi.model.UserAd.class));
    }

    @Test
    void surfedUserAdConsumesRemainingBudgetAndCreditsViewer() {
        User owner = User.builder().id(8L).email("owner@example.com").build();
        User viewer = User.builder().id(4L).email("player@example.com").build();
        UserStats stats = UserStats.builder().annualPoints(50).monthlyPoints(50).build();
        Ad ad = Ad.builder().id(5L).active(true).reward(20).budget(40).proposer(owner).build();
        when(adRepository.findWithLockById(5L)).thenReturn(Optional.of(ad));
        when(userStatsRepository.findWithLockByUserEmail(viewer.getEmail())).thenReturn(Optional.of(stats));

        userService.createUserAd(viewer, 5L);

        assertEquals(20, ad.getBudget());
        assertEquals(70, stats.getAnnualPoints());
        assertEquals(70, stats.getMonthlyPoints());
        verify(adRepository).save(ad);
        verify(userAdRepository).save(any(com.soa.spiriponzi.model.UserAd.class));
    }

    @Test
    void surfedUserAdRejectsWhenRemainingBudgetCannotCoverReward() {
        User owner = User.builder().id(8L).email("owner@example.com").build();
        User viewer = User.builder().id(4L).email("player@example.com").build();
        Ad ad = Ad.builder().id(5L).active(true).reward(20).budget(0).proposer(owner).build();
        when(adRepository.findWithLockById(5L)).thenReturn(Optional.of(ad));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUserAd(viewer, 5L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(userAdRepository, never()).save(any(com.soa.spiriponzi.model.UserAd.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void surfedUserAdRejectsSecondClaimOnSameDayWithoutSpendingBudget() {
        User owner = User.builder().id(8L).email("owner@example.com").build();
        User viewer = User.builder().id(4L).email("player@example.com").build();
        Ad ad = Ad.builder().id(5L).active(true).reward(20).budget(40).proposer(owner).build();
        when(adRepository.findWithLockById(5L)).thenReturn(Optional.of(ad));
        when(userAdRepository.existsByAdIdAndUserIdAndCompletedAt(5L, 4L, java.time.LocalDate.now()))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUserAd(viewer, 5L)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(40, ad.getBudget());
        verify(userAdRepository, never()).save(any(com.soa.spiriponzi.model.UserAd.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }
}
