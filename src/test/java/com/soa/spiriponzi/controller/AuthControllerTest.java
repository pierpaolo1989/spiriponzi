package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.dto.ResetPasswordFormDto;
import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.TheoremReachCallbackService;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.WithdrawService;
import com.soa.spiriponzi.service.OfferwallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private UserService userService;
    private OfferwallService offerwallService;
    private TheoremReachCallbackService theoremReachCallbackService;
    private AuthController controller;
    private final User user = User.builder()
            .id(10L)
            .email(ControllerTestSupport.EMAIL)
            .btcWallet("btc-address")
            .ethWallet("eth-address")
            .paypalEmail("pay@example.com")
            .build();

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        userService = mock(UserService.class);
        offerwallService = mock(OfferwallService.class);
        theoremReachCallbackService = mock(TheoremReachCallbackService.class);
        controller = new AuthController(
                userService, mock(WithdrawService.class), offerwallService, theoremReachCallbackService);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(user);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void publicPagesReturnTheirTemplates() {
        assertEquals("index", controller.home());
        assertEquals("faq", controller.faq());
        assertEquals("terms", controller.terms());
        assertEquals("login", controller.loginForm());
        assertEquals("recover-password", controller.recoverPassword(new ExtendedModelMap()));
        assertEquals("register", controller.showRegistrationForm(new ExtendedModelMap(), null));
    }

    @Test
    void showRegistrationFormAddsNewUserDto() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("register", controller.showRegistrationForm(model, null));

        org.junit.jupiter.api.Assertions.assertInstanceOf(UserDto.class, model.get("user"));
    }

    @Test
    void showRegistrationFormPrefillsReferralCodeFromInviteLink() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("register", controller.showRegistrationForm(model, "friend-code"));

        UserDto dto = (UserDto) model.get("user");
        assertEquals("friend-code", dto.getReferralCode());
    }

    @Test
    void registrationSavesNewUserAndRedirects() {
        UserDto dto = new UserDto();
        dto.setEmail(ControllerTestSupport.EMAIL);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(null);
        BindingResult result = mock(BindingResult.class);

        assertEquals("redirect:/register?success",
                controller.registration(dto, result, new ExtendedModelMap()));

        verify(userService).saveUser(dto);
    }

    @Test
    void registrationReturnsFormWhenEmailAlreadyExists() {
        UserDto dto = new UserDto();
        dto.setEmail(ControllerTestSupport.EMAIL);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(user);
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("register", controller.registration(dto, result, model));

        assertSame(dto, model.get("user"));
        verify(result).rejectValue("email", null, "There is already an account registered with this email");
    }

    @Test
    void registrationRejectsInvalidReferralCode() {
        UserDto dto = new UserDto();
        dto.setEmail("new@example.com");
        dto.setReferralCode("invalid-code");
        when(userService.findByEmail(dto.getEmail())).thenReturn(null);
        when(userService.isValidReferralCode("invalid-code")).thenReturn(false);
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);

        assertEquals("register", controller.registration(dto, result, new ExtendedModelMap()));

        verify(result).rejectValue("referralCode", null, "Referral code is not valid");
    }

    @Test
    void activationRedirectsAccordingToTokenResult() {
        when(userService.activateAccount("valid-token")).thenReturn(true);
        when(userService.activateAccount("invalid-token")).thenReturn(false);

        assertEquals("redirect:/login?activated", controller.activateAccount("valid-token"));
        assertEquals("redirect:/login?activationError", controller.activateAccount("invalid-token"));
    }

    @Test
    void resendingActivationRedirectsWithGenericConfirmation() {
        assertEquals("redirect:/login?activationSent", controller.resendActivationEmail("person@example.com"));

        verify(userService).resendActivationEmail("person@example.com");
    }

    @Test
    void recoverPasswordRequestsRecoveryAndRedirects() {
        RecoverPasswordDto dto = RecoverPasswordDto.builder()
                .email(ControllerTestSupport.EMAIL)
                .confirmEmail(ControllerTestSupport.EMAIL)
                .build();
        BindingResult result = mock(BindingResult.class);

        assertEquals("redirect:/recover-password?success",
                controller.recoverPassword(dto, result, new ExtendedModelMap()));

        verify(userService).recoverPassword(dto);
    }

    @Test
    void recoverPasswordReturnsFormWhenEmailConfirmationDoesNotMatch() {
        RecoverPasswordDto dto = RecoverPasswordDto.builder()
                .email(ControllerTestSupport.EMAIL)
                .confirmEmail("other@example.com")
                .build();
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("recover-password", controller.recoverPassword(dto, result, model));

        assertSame(dto, model.get("password"));
        verify(result).rejectValue("confirmEmail", null, "Email addresses do not match");
        verify(userService, never()).recoverPassword(dto);
    }

    @Test
    void resetPasswordRedirectsAfterSuccessfulReset() {
        ResetPasswordFormDto dto = new ResetPasswordFormDto();
        dto.setToken("reset-token");
        dto.setPassword("new-password");
        dto.setConfirmPassword("new-password");
        when(userService.resetPassword("reset-token", "new-password")).thenReturn(true);

        assertEquals("redirect:/login?passwordReset",
                controller.resetPassword(dto, mock(BindingResult.class), new ExtendedModelMap()));
    }

    @Test
    void resetPasswordReturnsFormWhenPasswordsDoNotMatch() {
        ResetPasswordFormDto dto = new ResetPasswordFormDto();
        dto.setToken("reset-token");
        dto.setPassword("new-password");
        dto.setConfirmPassword("different-password");
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);

        assertEquals("reset-password",
                controller.resetPassword(dto, result, new ExtendedModelMap()));

        verify(result).rejectValue("confirmPassword", null, "Passwords do not match");
        verify(userService, never()).resetPassword("reset-token", "new-password");
    }

    @Test
    void updateUserRedirectsAfterSavingExistingUser() {
        UpdateUserDto dto = UpdateUserDto.builder().email(ControllerTestSupport.EMAIL).build();
        BindingResult result = mock(BindingResult.class);

        assertEquals("redirect:/profile?success",
                controller.updateUserStats(dto, result, new ExtendedModelMap()));

        verify(userService).updateUser(dto);
    }

    @Test
    void updateUserRejectsMissingAuthenticatedUser() {
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(null);
        UpdateUserDto dto = UpdateUserDto.builder().email(ControllerTestSupport.EMAIL).build();
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateUserStats(dto, result, model));
        verify(userService, never()).updateUser(dto);
    }

    @Test
    void userPagesAddAuthenticatedUserAndAdsWhereRequired() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<ADDto> ads = List.of(ADDto.builder().id(1L).title("Surf").build());
        when(userService.findAdsByUser(10L)).thenReturn(ads);

        assertEquals("dashboard", controller.dashboard(model));
        assertEquals(user, model.get("user"));
        assertEquals(true, model.get("showSidebarAd"));
        assertEquals("surf-ads", controller.surfAds(model));
        assertEquals(ads, model.get("ads"));
        when(theoremReachCallbackService.isConfigured()).thenReturn(true);
        when(theoremReachCallbackService.createIframeUrl("10"))
                .thenReturn("https://theoremreach.com/respondent_entry/direct?api_key=api-key&user_id=10"
                        + "&transaction_id=transaction-id");
        assertEquals("survey", controller.survey(model));
        assertEquals(true, model.get("theoremReachEnabled"));
        assertEquals("https://theoremreach.com/respondent_entry/direct?api_key=api-key&user_id=10"
                        + "&transaction_id=transaction-id",
                model.get("theoremReachUrl"));
        assertEquals(23067, model.get("cpxAppId"));
        assertEquals("10", model.get("cpxExtUserId"));
        assertEquals(ControllerTestSupport.EMAIL, model.get("cpxEmail"));
        assertEquals(ControllerTestSupport.EMAIL, model.get("cpxUsername"));
        assertEquals("academy", controller.academy(model));
        assertEquals("withdraw-requests", controller.withdrawRequests(model));
        assertEquals("report", controller.report(model));
        assertEquals("games", controller.games(model));
        when(offerwallService.isConfigured()).thenReturn(true);
        when(offerwallService.createSignedWallUrl("10"))
                .thenReturn("https://offerwall.gg/wall/public-key?userId=10&signature=signature");
        when(offerwallService.isEarnwallConfigured()).thenReturn(true);
        when(offerwallService.getEarnwallPlacementKey()).thenReturn("placement-key");
        when(offerwallService.createEarnwallWallUrl("10"))
                .thenReturn("https://earnwall.net/offerwall/placement-key/10");
        ReflectionTestUtils.setField(controller, "publicBaseUrl", "https://www.spiriponzi.cloud");
        assertEquals("offerwall", controller.offerwall(model));
        assertEquals(true, model.get("offerwallEnabled"));
        assertEquals("https://offerwall.gg/wall/public-key?userId=10&signature=signature",
                model.get("offerwallUrl"));
        assertEquals("https://www.spiriponzi.cloud/callbacks/offerwall", model.get("offerwallCallbackUrl"));
        assertEquals(true, model.get("earnwallEnabled"));
        assertEquals("placement-key", model.get("earnwallPlacementKey"));
        assertEquals("https://earnwall.net/offerwall/placement-key/10", model.get("earnwallUrl"));
        when(userService.getOrCreateReferralCode(10L)).thenReturn("REFERRAL123");
        assertEquals("profile", controller.profile(model));
        assertSame(user, model.get("user"));
        assertEquals("REFERRAL123", model.get("referralCode"));
        UpdateUserDto profileDto = (UpdateUserDto) model.get("updateUser");
        assertEquals(ControllerTestSupport.EMAIL, profileDto.getEmail());
        assertEquals("btc-address", profileDto.getBtcWallet());
        assertEquals("eth-address", profileDto.getEthWallet());
        assertEquals("pay@example.com", profileDto.getPaypalEmail());
        verify(userService).findAdsByUser(10L);
    }
}
