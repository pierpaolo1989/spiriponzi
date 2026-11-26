package com.soa.spiriponzi.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.mail.MessagingException;
import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.security.CustomUserDetailsService;
import com.soa.spiriponzi.controller.AdminMfaController;
import com.soa.spiriponzi.controller.PromoCodeAdminController;
import com.soa.spiriponzi.controller.PromoCodeController;
import com.soa.spiriponzi.service.AdminMfaService;
import com.soa.spiriponzi.service.EmailService;
import com.soa.spiriponzi.service.CloudflareAnalyticsService;
import com.soa.spiriponzi.service.PromoCodeService;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.ActivationEmailResendResult;
import com.soa.spiriponzi.service.RenderService;
import com.soa.spiriponzi.service.WithdrawServiceImpl;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = {
        AdminController.class, AdminMfaController.class, PromoCodeAdminController.class, PromoCodeController.class
}, properties = {
        "app.admin.username=test-admin",
        "app.admin.password=local-test-password",
        "app.admin.mfa.email=admin@example.com",
        "cloudflare.web-analytics.token=test-cloudflare-token"
})
@Import({SecurityConfig.class, CustomUserDetailsService.class, AdminMfaService.class})
class AdminLoginWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository users;
    @MockBean private AdRepository ads;
    @MockBean private ClaimRepository claims;
    @MockBean private WithdrawRepository withdrawals;
    @MockBean private WithdrawServiceImpl withdrawService;
    @MockBean private RenderService renderService;
    @MockBean private CloudflareAnalyticsService cloudflareAnalyticsService;
    @MockBean private PromoCodeService promoCodeService;
    @MockBean private UserService userService;
    @MockBean private EmailService emailService;

    @Test
    void repeatedLoginsWithSeparateSessionsReachAdminDashboard() throws Exception {
        when(withdrawals.findByStatusInOrderByDateRequestAsc(List.of("PENDING"), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of()));
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = mvc.perform(post("/login").servletPath("/login")
                            .param("username", "test-admin").param("password", "local-test-password"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/mfa")).andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
            verify(emailService, times(attempt + 1)).sendAdminLoginCode(eq("admin@example.com"), codes.capture());
            String code = codes.getAllValues().get(attempt);

            mvc.perform(get("/admin").servletPath("/admin").session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("http://localhost/login"));
            var verified = mvc.perform(post("/admin/mfa").servletPath("/admin/mfa")
                            .session(session)
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .param("code", code))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin")).andReturn();
            mvc.perform(get("/admin").servletPath("/admin")
                            .session((MockHttpSession) verified.getRequest().getSession(false)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/login").servletPath("/login")
                        .param("username", "test-admin").param("password", "incorrect"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void adminDashboardUsesUserStylePostLogoutMenu() throws Exception {
        when(withdrawals.findByStatusInOrderByDateRequestAsc(List.of("PENDING"), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of()));
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/admin").servletPath("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"adminUserDropdown\"")))
                .andExpect(content().string(containsString("fas fa-sign-out-alt fa-sm fa-fw mr-2 text-gray-400")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("method=\"post\"")))
                .andExpect(content().string(containsString("static.cloudflareinsights.com/beacon.min.js")))
                .andExpect(content().string(containsString("test-cloudflare-token")))
                .andExpect(content().string(containsString("Cloudflare Web Analytics")))
                .andExpect(content().string(containsString("/admin/cloudflare/analytics")));
    }

    @Test
    void adminAnalyticsEndpointReturnsNoStoreAggregates() throws Exception {
        when(cloudflareAnalyticsService.getPageLoads()).thenReturn(
                new CloudflareAnalyticsService.AnalyticsSnapshot(15,
                        List.of(new CloudflareAnalyticsService.AnalyticsDimension("IT", 15)),
                        List.of(new CloudflareAnalyticsService.DailyPageLoads("2026-11-25", 15)),
                        List.of(new CloudflareAnalyticsService.AnalyticsDimension("/", 15)),
                        List.of(new CloudflareAnalyticsService.AnalyticsDimension("Chrome", 15)),
                        List.of(new CloudflareAnalyticsService.AnalyticsDimension("desktop", 15)),
                        List.of(new CloudflareAnalyticsService.AnalyticsDimension("Direct", 15))));
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/admin/cloudflare/analytics").servletPath("/admin/cloudflare/analytics").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().json("""
                        {
                          "pageLoads":15,
                          "countries":[{"value":"IT","pageLoads":15}],
                          "daily":[{"date":"2026-11-25","pageLoads":15}],
                          "topPages":[{"value":"/","pageLoads":15}],
                          "browsers":[{"value":"Chrome","pageLoads":15}],
                          "devices":[{"value":"desktop","pageLoads":15}],
                          "referrers":[{"value":"Direct","pageLoads":15}]
                        }
                        """));
    }

    @Test
    void promoCodePagesAreAvailableToTheirIntendedUsersAndRedeemingRequiresCsrf() throws Exception {
        when(promoCodeService.listCodes()).thenReturn(List.of());
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/admin/promo-codes").servletPath("/admin/promo-codes").session(session))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("Genera un codice")))
                        .andExpect(content().string(containsString("Codici promozionali")))
                        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Amministrazione"))));

        mvc.perform(get("/promotions").servletPath("/promotions").with(user("player").roles("USER")))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("Redeem a promo code")))
                        .andExpect(content().string(containsString("/css/user-dark-mode.css")))
                        .andExpect(content().string(containsString("data-aa=\"2463408\"")))
                        .andExpect(content().string(containsString("Discord · Coming soon")))
                        .andExpect(content().string(containsString("Telegram · Coming soon")))
                        .andExpect(content().string(containsString("Copyright")));

        mvc.perform(post("/promotions").servletPath("/promotions")
                                .with(user("player").roles("USER")).param("code", "SP-TEST"))
                        .andExpect(status().isForbidden());

        when(promoCodeService.redeemCode("SP-TEST", "player")).thenReturn(100);
        mvc.perform(post("/promotions").servletPath("/promotions")
                                .with(user("player").roles("USER"))
                                .with(SecurityMockMvcRequestPostProcessors.csrf())
                                .param("code", "SP-TEST"))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/promotions"));
        verify(promoCodeService).redeemCode("SP-TEST", "player");
    }

    @Test
    void adminCanViewUnactivatedUsersAndRequestActivationEmailResend() throws Exception {
        when(users.findTop10ByEnabledFalseOrderByIdDesc())
                .thenReturn(List.of(com.soa.spiriponzi.model.User.builder()
                        .id(41L).email("pending@example.com").enabled(false).build()));
        when(withdrawals.findByStatusInOrderByDateRequestAsc(List.of("PENDING"), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of()));
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/admin").servletPath("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Utenti da attivare")))
                .andExpect(content().string(containsString("pending@example.com")))
                .andExpect(content().string(containsString("/admin/users/41/resend-activation")));

        when(userService.resendActivationEmailForAdmin(41L)).thenReturn(ActivationEmailResendResult.SENT);
        mvc.perform(post("/admin/users/41/resend-activation").servletPath("/admin/users/41/resend-activation")
                        .session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
        verify(userService).resendActivationEmailForAdmin(41L);
    }

    @Test
    void adminDashboardHidesResendButtonDuringActivationCooldown() throws Exception {
        when(users.findTop10ByEnabledFalseOrderByIdDesc())
                .thenReturn(List.of(com.soa.spiriponzi.model.User.builder()
                        .id(42L).email("recent@example.com").enabled(false).build()));
        when(userService.isActivationEmailCoolingDown(42L)).thenReturn(true);
        when(withdrawals.findByStatusInOrderByDateRequestAsc(List.of("PENDING"), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of()));
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/admin").servletPath("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Email inviata da meno di un minuto; aggiorna tra poco per reinviare")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/admin/users/42/resend-activation"))));
    }

    @Test
    void homepagePublishesSocialPreviewImageMetadata() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "property=\"og:image\" content=\"https://www.spiriponzi.cloud/img/main_page.jpg\"")))
                .andExpect(content().string(containsString(
                        "name=\"twitter:card\" content=\"summary_large_image\"")))
                .andExpect(content().string(containsString(
                        "name=\"twitter:image\" content=\"https://www.spiriponzi.cloud/img/main_page.jpg\"")));
    }

    @Test
    void promoCodeAdminPageHasDashboardLogoutAndDeleteAction() throws Exception {
        var promo = new com.soa.spiriponzi.model.PromoCode();
        promo.setId(8L);
        promo.setCode("SP-TEST-CODE");
        promo.setPoints(200);
        promo.setMaxRedemptions(10);
        promo.setRedemptionsUsed(2);
        promo.setExpiresOn(java.time.LocalDate.of(2026, 12, 31));
        promo.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 26, 12, 0));
        when(promoCodeService.listCodes()).thenReturn(List.of(promo));
        MockHttpSession session = loginAdmin();

        mvc.perform(get("/admin/promo-codes").servletPath("/admin/promo-codes").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard amministratore")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("/admin/promo-codes/8/delete")))
                .andExpect(content().string(containsString("Elimina")));

        mvc.perform(post("/admin/promo-codes/8/delete").servletPath("/admin/promo-codes/8/delete")
                        .session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/promo-codes"));
        verify(promoCodeService).deleteCode(8L);
    }

    @Test
    void incorrectVerificationCodeDoesNotAuthorizeAdminSession() throws Exception {
        var login = mvc.perform(post("/login").servletPath("/login")
                        .param("username", "test-admin").param("password", "local-test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mfa"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminLoginCode(eq("admin@example.com"), code.capture());
        String incorrectCode = code.getValue().equals("000000") ? "000001" : "000000";

        mvc.perform(post("/admin/mfa").servletPath("/admin/mfa")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("code", incorrectCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mfa?error"));
        mvc.perform(get("/admin").servletPath("/admin").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void emailDeliveryFailureDoesNotAuthorizeAdminSession() throws Exception {
        doThrow(new MessagingException("SMTP unavailable"))
                .when(emailService).sendAdminLoginCode(eq("admin@example.com"), anyString());

        var login = mvc.perform(post("/login").servletPath("/login")
                        .param("username", "test-admin").param("password", "local-test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?mfaEmailError"))
                .andReturn();

        mvc.perform(get("/admin").servletPath("/admin")
                        .session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    private MockHttpSession loginAdmin() throws Exception {
        var login = mvc.perform(post("/login").servletPath("/login")
                        .param("username", "test-admin").param("password", "local-test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mfa"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminLoginCode(eq("admin@example.com"), code.capture());
        var verified = mvc.perform(post("/admin/mfa").servletPath("/admin/mfa")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("code", code.getValue()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();
        return (MockHttpSession) verified.getRequest().getSession(false);
    }

    @Test
    void missingPageShowsBrandedNotFoundPage() throws Exception {
        mvc.perform(get("/error").servletPath("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/a-page-that-does-not-exist")
                        .with(user("player").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Pagina non trovata")))
                .andExpect(content().string(containsString("Torna alla home")));
    }

    @Test
    void accessDeniedShowsBrandedErrorPage() throws Exception {
        mvc.perform(get("/error").servletPath("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin-only")
                        .with(user("player").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Accesso negato")))
                .andExpect(content().string(containsString("Non hai i permessi")));
    }
}
