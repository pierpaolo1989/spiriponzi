package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.WithdrawService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private UserService userService;
    private AuthController controller;
    private final User user = User.builder()
            .id(10L)
            .email(ControllerTestSupport.EMAIL)
            .btcWallet("btc-address")
            .ethWallet("eth-address")
            .build();

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        userService = mock(UserService.class);
        controller = new AuthController(userService, mock(WithdrawService.class));
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
        assertEquals("login", controller.loginForm());
        assertEquals("recover-password", controller.recoverPassword(new ExtendedModelMap()));
        assertEquals("register", controller.showRegistrationForm(new ExtendedModelMap()));
    }

    @Test
    void showRegistrationFormAddsNewUserDto() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("register", controller.showRegistrationForm(model));

        org.junit.jupiter.api.Assertions.assertInstanceOf(UserDto.class, model.get("user"));
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
    void recoverPasswordSendsRecoveryForExistingUser() {
        RecoverPasswordDto dto = RecoverPasswordDto.builder().email(ControllerTestSupport.EMAIL).build();
        BindingResult result = mock(BindingResult.class);

        assertEquals("redirect:/recover-password?success",
                controller.registration(dto, result, new ExtendedModelMap()));

        verify(userService).recoverPassword(dto);
    }

    @Test
    void recoverPasswordReturnsFormWhenUserDoesNotExist() {
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(null);
        RecoverPasswordDto dto = RecoverPasswordDto.builder().email(ControllerTestSupport.EMAIL).build();
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("recover-password", controller.registration(dto, result, model));

        assertSame(dto, model.get("password"));
        verify(result).rejectValue("email", null, "There is no account registered with this email");
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
    void updateUserReturnsProfileWhenUserDoesNotExist() {
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(null);
        UpdateUserDto dto = UpdateUserDto.builder().email(ControllerTestSupport.EMAIL).build();
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("profile", controller.updateUserStats(dto, result, model));

        assertSame(dto, model.get("user"));
        verify(result).rejectValue("email", null, "No user was found registered with this email");
    }

    @Test
    void userPagesAddAuthenticatedUserAndAdsWhereRequired() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<ADDto> ads = List.of(ADDto.builder().id(1L).title("Surf").build());
        when(userService.findAdsByUser(10L)).thenReturn(ads);

        assertEquals("dashboard", controller.dashboard(model));
        assertEquals(user, model.get("user"));
        assertEquals("surf-ads", controller.surfAds(model));
        assertEquals(ads, model.get("ads"));
        assertEquals("survey", controller.survey(model));
        assertEquals("academy", controller.academy(model));
        assertEquals("withdraw-requests", controller.withdrawRequests(model));
        assertEquals("report", controller.report(model));
        assertEquals("games", controller.games(model));
        assertEquals("profile", controller.profile(model));
        assertSame(user, model.get("user"));
        UpdateUserDto profileDto = (UpdateUserDto) model.get("updateUser");
        assertEquals(ControllerTestSupport.EMAIL, profileDto.getEmail());
        assertEquals("btc-address", profileDto.getBtcWallet());
        assertEquals("eth-address", profileDto.getEthWallet());
        verify(userService).findAdsByUser(10L);
    }
}
