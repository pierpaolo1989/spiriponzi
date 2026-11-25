package com.soa.spiriponzi.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.config.CaptchaSettings;
import com.soa.spiriponzi.service.AffiliateService;
import com.soa.spiriponzi.controller.AffiliateController;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.model.WithdrawRequest;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.service.WithdrawServiceImpl;
import com.soa.spiriponzi.service.UserService;
import com.soa.spiriponzi.service.OfferwallService;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.service.ADService;
import com.soa.spiriponzi.service.CloudflareAnalyticsService;
import com.soa.spiriponzi.service.RenderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, AuthController.class, AdController.class, WihdrawController.class,
        AffiliateController.class})
@Import(SecurityConfig.class)
class AdminWithdrawalWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserRepository users;
    @MockBean private AdRepository ads;
    @MockBean private ClaimRepository claims;
    @MockBean private WithdrawRepository withdrawals;
    @MockBean private WithdrawServiceImpl service;
    @MockBean private UserService userService;
    @MockBean private OfferwallService offerwallService;
    @MockBean private CaptchaService captchaService;
    @MockBean private CloudflareAnalyticsService cloudflareAnalyticsService;
    @MockBean private RenderService renderService;
    @MockBean private ADService adService;
    @MockBean private AffiliateService affiliateService;
    @MockBean(name = "captchaSettings") private CaptchaSettings captchaSettings;

    private MockHttpSession session(String role) {
        return session(role, "test");
    }

    private MockHttpSession session(String role, String email) {
        MockHttpSession session = new MockHttpSession();
        var auth = new UsernamePasswordAuthenticationToken(email, "unused",
                List.of(new SimpleGrantedAuthority(role)));
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(auth));
        return session;
    }

    @Test
    void homeRoutesRenderAadsAdvertisement() throws Exception {
        for (String path : List.of("/", "/index")) {
            String html = mvc.perform(get(path).servletPath(path.equals("/") ? "" : path))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("data-aa=\"2462874\""));
            assertTrue(html.contains("src=\"https://acceptable.a-ads.com/2462874/?size=Adaptive\""));
            assertTrue(html.contains("Bitcoin (BTC), Ethereum (ETH), or PayPal (EUR)"));
        }
    }

    @Test
    void faqAndOfferwallRenderLazyAadsAdvertisements() throws Exception {
        String faq = mvc.perform(get("/faq").servletPath("/faq"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(faq.contains("data-aa=\"2462874\""));
        assertTrue(faq.contains("loading=\"lazy\" width=\"720\" height=\"150\""));
        assertTrue(faq.contains("Bitcoin (BTC), Ethereum (ETH), or PayPal (EUR)"));

        User user = User.builder().id(1L).email("test").userStats(UserStats.builder().build()).build();
        when(userService.findByEmail("test")).thenReturn(user);
        String offerwall = mvc.perform(get("/offerwall").servletPath("/offerwall")
                        .session(session("ROLE_USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(offerwall.contains("data-aa=\"2462874\""));
        assertTrue(offerwall.contains("loading=\"lazy\" width=\"720\" height=\"150\""));
    }

    @Test
    void surfAndCreateAdsPagesRenderAadsAdvertisement() throws Exception {
        User user = User.builder().id(1L).email("test").userStats(UserStats.builder().build()).build();
        when(userService.findByEmail("test")).thenReturn(user);
        when(userService.findAdsByUser(1L)).thenReturn(List.of());
        when(adService.getProposedAds(1L)).thenReturn(List.of());

        for (String path : List.of("/surf-ads", "/create-ads")) {
            String html = mvc.perform(get(path).servletPath(path).session(session("ROLE_USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("data-aa=\"2462874\""));
            if (path.equals("/surf-ads")) {
                int priorityNote = html.indexOf("Ads with higher remaining budgets appear first.");
                int adList = html.indexOf("class=\"card-body ad-container\"");
                assertTrue(priorityNote > 0 && priorityNote < adList);
            } else {
                assertTrue(html.contains("id=\"budget\""));
                assertTrue(html.contains("id=\"fundedViewsEstimate\""));
            }
            assertTrue(html.contains("data-ad-slot=\"2465731755\""));
        }
    }

    @Test
    void dashboardAdBlockNoticeIsNotDismissible() throws Exception {
        User user = User.builder().email("test").userStats(UserStats.builder().build()).build();
        when(userService.findByEmail("test")).thenReturn(user);

        String html = mvc.perform(get("/dashboard").servletPath("/dashboard").session(session("ROLE_USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("class=\"nav-item d-none d-lg-block px-3 py-2 sidebar-ad\""));
        assertTrue(html.contains("data-ad-slot=\"2465731755\""));
        assertTrue(html.indexOf("Offerwall") < html.indexOf("data-ad-slot=\"2465731755\""));
        assertTrue(html.contains("id=\"themeToggle\""));
        assertTrue(html.contains("/css/user-dark-mode.css"));
        assertTrue(html.contains("spiriponzi.theme"));
        int modalStart = html.lastIndexOf("<div class=\"modal fade adblock-notice\"");
        int modalEnd = html.indexOf("<!-- Bootstrap core JavaScript-->", modalStart);
        assertTrue(modalStart >= 0);
        assertTrue(modalEnd > modalStart);
        String modal = html.substring(modalStart, modalEnd);
        assertTrue(modal.contains("data-backdrop=\"static\""));
        assertTrue(modal.contains("data-keyboard=\"false\""));
        assertTrue(modal.contains("Allow ads to continue"));
        assertTrue(modal.contains("Check again"));
        assertFalse(modal.contains("data-dismiss=\"modal\""));
    }

    @Test
    void rendersAdminPageAndRequiresCsrfForApproval() throws Exception {
        User user = User.builder().email("user@example.com")
                .userStats(UserStats.builder().annualPoints(100).build()).build();
        WithdrawRequest request = WithdrawRequest.builder().id(1L).user(user).amount(3000).status("PENDING")
                .currency("PAYPAL").destination("pay@example.com")
                .grossEur(new BigDecimal("0.50")).feeEur(new BigDecimal("0.10")).build();
        when(service.pendingRequests(0)).thenReturn(new PageImpl<>(List.of(request)));
        MockHttpSession session = session("ROLE_ADMIN");
        MvcResult page = mvc.perform(get("/admin/withdrawals").servletPath("/admin/withdrawals").session(session))
                .andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString();
        assertTrue(html.contains("pay@example.com"));
        assertTrue(html.contains("href=\"/img/favicon.ico\""));
        Matcher token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(token.find(), "The form must include a CSRF token");

        mvc.perform(post("/admin/withdrawals/1/approve").servletPath("/admin/withdrawals/1/approve").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/withdrawals/1/approve").servletPath("/admin/withdrawals/1/approve")
                        .session(session).param("_csrf", token.group(1)))
                .andExpect(status().is3xxRedirection());
        verify(service).approve(1L);
    }

    @Test
    void adminDashboardShowsUserPointsAndPendingWithdrawals() throws Exception {
        User user = User.builder().id(8L).email("points@example.com")
                .userStats(UserStats.builder().annualPoints(1234).build()).build();
        WithdrawRequest pending = WithdrawRequest.builder().id(12L).user(user).amount(3000)
                .currency("PAYPAL").status("PENDING")
                .grossEur(new BigDecimal("0.50")).feeEur(new BigDecimal("0.10")).build();
        when(users.sumAnnualPoints()).thenReturn(1234L);
        when(users.findTop5ByOrderByIdDesc()).thenReturn(List.of(user));
        when(withdrawals.findByStatusInOrderByDateRequestAsc(
                List.of("PENDING"), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(pending)));

        MvcResult page = mvc.perform(get("/admin").servletPath("/admin").session(session("ROLE_ADMIN")))
                .andExpect(status().isOk()).andReturn();

        String html = page.getResponse().getContentAsString();
        assertTrue(html.contains("spiriponzi-mark.svg"));
        assertTrue(html.contains("1234 punti"));
        assertTrue(html.contains("Prelievi da approvare"));
        assertTrue(html.contains("points@example.com"));
        assertTrue(html.contains("Gestisci prelievi"));
        assertTrue(html.contains("Servizi Render"));
        assertTrue(html.contains("/admin/render/services"));
        assertTrue(html.contains("id=\"sidebarToggle\""));
        assertTrue(html.contains("id=\"sidebarToggleTop\""));
        assertTrue(html.contains("th:action=\"/logout\"") || html.contains("action=\"/logout\""));
        assertTrue(html.contains("aria-label=\"Esci\""));
        assertTrue(html.contains("fas fa-sign-out-alt"));
        assertFalse(html.contains("adminUserDropdown"));
        assertFalse(html.contains("fas fa-user-circle"));
        assertTrue(html.indexOf("render-heading") > html.indexOf("Prelievi da approvare"));
    }

    @Test
    void reportUsesSingularTitleAndRendersSidebarLogoAndAd() throws Exception {
        User user = User.builder().id(10L).email("test")
                .userStats(UserStats.builder().build()).build();
        when(userService.findByEmail("test")).thenReturn(user);

        String html = mvc.perform(get("/report").servletPath("/report").session(session("ROLE_USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("<h1 class=\"h3 mb-0 text-gray-800\">Report</h1>"));
        assertFalse(html.contains(">Reports</h1>"));
        assertTrue(html.contains("src=\"/img/spiriponzi-mark.svg\""));
        assertTrue(html.contains("class=\"nav-item d-none d-lg-block px-3 py-2 sidebar-ad\""));
        assertTrue(html.contains("data-ad-slot=\"2465731755\""));
    }

    @Test
    void affiliatePageShowsBannerCodeAndRewardProgress() throws Exception {
        User user = User.builder().id(10L).email("affiliate@example.test")
                .userStats(UserStats.builder().build()).build();
        when(userService.findByEmail("affiliate@example.test")).thenReturn(user);
        when(userService.getOrCreateReferralCode(10L)).thenReturn("AFFILIATE10");
        when(affiliateService.getStats(10L)).thenReturn(new AffiliateService.AffiliateStats(12, 0, 88));

        String html = mvc.perform(get("/affiliate").servletPath("/affiliate")
                        .session(session("ROLE_USER", "affiliate@example.test")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("affiliate-banner.svg"));
        assertTrue(html.contains("/affiliate/click/AFFILIATE10"));
        assertTrue(html.contains("id=\"affiliateEmbedCode\""));
        assertTrue(html.contains("12"));
        assertTrue(html.contains("88"));
    }

    @Test
    void affiliateClickRedirectIsPublicAndCreatesVisitorCookie() throws Exception {
        String visitorToken = "A".repeat(43);
        when(affiliateService.trackClick("AFFILIATE10", null, null))
                .thenReturn(new AffiliateService.ClickResult(visitorToken, true, true, 1, 0));

        MvcResult result = mvc.perform(get("/affiliate/click/AFFILIATE10")
                        .servletPath("/affiliate/click/AFFILIATE10"))
                .andExpect(status().isFound())
                .andReturn();

        assertTrue(result.getResponse().getHeader("Location").contains("/register?ref=AFFILIATE10"));
        assertTrue(result.getResponse().getHeader("Set-Cookie").contains("HttpOnly"));
        verify(affiliateService).trackClick("AFFILIATE10", null, null);
    }

    @Test
    void renderServicesAreRestrictedToAdmins() throws Exception {
        when(renderService.listServices()).thenReturn(List.of(
                new RenderService.RenderServiceInfo("web", "web_service", "not_suspended")));
        mvc.perform(get("/admin/render/services").servletPath("/admin/render/services"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/render/services").servletPath("/admin/render/services").session(session("ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/render/services").servletPath("/admin/render/services").session(session("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].name").value("web"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].suspended").value("not_suspended"));
    }

    @Test
    void regularUserCannotAccessAdminWithdrawals() throws Exception {
        mvc.perform(get("/admin/withdrawals").servletPath("/admin/withdrawals").session(session("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userPageRendersAndWithdrawalRequiresCsrfAndCaptcha() throws Exception {
        User user = User.builder().email("test").paypalEmail("pay@example.com")
                .userStats(UserStats.builder().annualPoints(3000).build()).build();
        when(userService.findByEmail("test")).thenReturn(user);
        when(captchaSettings.getSite()).thenReturn("test-site-key");
        when(captchaService.getReCaptchaSite()).thenReturn("production-site-key");
        when(captchaService.getReCaptchaSecret()).thenReturn("production-secret-key");
        MockHttpSession session = session("ROLE_USER");
        MvcResult page = mvc.perform(get("/withdraw-requests").servletPath("/withdraw-requests").session(session))
                .andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString();
        assertTrue(html.contains("PayPal"));
        assertTrue(html.contains("href=\"/img/favicon.ico\""));
        assertTrue(html.contains("data-paypal=\"pay@example.com\""));
        Matcher token = Pattern.compile("name=\"_csrf\" content=\"([^\"]+)\"").matcher(html);
        assertTrue(token.find(), "The page must expose a CSRF token for its request");
        String json = "{\"amount\":3000,\"currency\":\"PAYPAL\",\"destination\":\"pay@example.com\",\"captchaResponse\":\"captcha-token\"}";
        mvc.perform(post("/withdraws").servletPath("/withdraws").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(post("/withdraws").servletPath("/withdraws").session(session)
                        .header("X-CSRF-TOKEN", token.group(1))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
        verify(captchaService).processResponse("captcha-token");
    }

    @Test
    void profileRendersShareLinksAndValidatesPaypalForAuthenticatedUser() throws Exception {
        String email = "user@example.com";
        User user = User.builder().id(1L).email(email).paypalEmail("pay@example.com")
                .userStats(UserStats.builder().annualPoints(3000).build()).build();
        when(userService.findByEmail(email)).thenReturn(user);
        when(userService.getOrCreateReferralCode(1L)).thenReturn("REF123");
        MockHttpSession session = session("ROLE_USER", email);
        MvcResult page = mvc.perform(get("/profile").servletPath("/profile").session(session))
                .andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString();
        assertTrue(html.contains("pay@example.com"));
        assertTrue(html.contains("href=\"/img/favicon.ico\""));
        assertTrue(html.contains("https://www.spiriponzi.cloud/register?ref=REF123"));
        assertTrue(html.contains("https://wa.me/"));
        assertTrue(html.contains("https://t.me/share/url"));
        assertTrue(html.contains("mailto:"));
        assertTrue(html.contains("https%3A%2F%2Fwww.spiriponzi.cloud%2Fregister%3Fref%3DREF123"));
        assertTrue(html.contains("id=\"frame\""));
        assertTrue(html.contains("data-aa=\"2462874\""));
        Matcher token = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(token.find());

        MvcResult invalid = mvc.perform(post("/user-data").servletPath("/user-data").session(session)
                .param("_csrf", token.group(1)).param("email", email).param("paypalEmail", "not-an-email"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(invalid.getResponse().getContentAsString().contains("Enter a valid PayPal email address"));
        assertTrue(invalid.getResponse().getContentAsString().contains("REF123"));
        mvc.perform(post("/user-data").servletPath("/user-data").session(session)
                .param("_csrf", token.group(1)).param("email", email)
                .param("paypalEmail", "a".repeat(245) + "@example.com"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("PayPal email must be at most 254 characters")));
        verify(userService, never()).updateUser(any());

        mvc.perform(post("/user-data").servletPath("/user-data").session(session)
                .param("_csrf", token.group(1)).param("email", "other@example.com")
                .param("paypalEmail", "pay@example.com")).andExpect(status().isForbidden());
        verify(userService, never()).updateUser(any());

        mvc.perform(post("/user-data").servletPath("/user-data").session(session)
                .param("_csrf", token.group(1)).param("email", email).param("paypalEmail", "pay@example.com"))
                .andExpect(status().is3xxRedirection());
        org.mockito.ArgumentCaptor<com.soa.spiriponzi.dto.UpdateUserDto> captor =
                org.mockito.ArgumentCaptor.forClass(com.soa.spiriponzi.dto.UpdateUserDto.class);
        verify(userService).updateUser(captor.capture());
        assertTrue("pay@example.com".equals(captor.getValue().getPaypalEmail()));
    }

    @Test
    void reportPageRendersAadsBeforeTheReportContent() throws Exception {
        String email = "user@example.com";
        when(userService.findByEmail(email)).thenReturn(User.builder().email(email)
                .userStats(UserStats.builder().annualPoints(3000).build()).build());
        MvcResult page = mvc.perform(get("/report").servletPath("/report").session(session("ROLE_USER", email)))
                .andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString();

        assertTrue(html.indexOf("id=\"frame\"") < html.indexOf("h1 class=\"h3 mb-0 text-gray-800\">Report"));
        assertTrue(html.contains("data-aa=\"2462874\""));
    }

    @Test
    void faviconIsPubliclyAccessibleAndLoginPageReferencesIt() throws Exception {
        mvc.perform(get("/img/favicon.ico").servletPath("/img/favicon.ico"))
                .andExpect(status().isOk());
        MvcResult page = mvc.perform(get("/login").servletPath("/login"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(page.getResponse().getContentAsString().contains("href=\"/img/favicon.ico\""));
        org.junit.jupiter.api.Assertions.assertFalse(page.getResponse().getContentAsString().contains("Sign in with Google"));
    }

    @Test
    void gamesPageRendersAadsInsideClaimModalWithoutAffectingGameFrames() throws Exception {
        User user = User.builder().email("test").userStats(UserStats.builder().build()).build();
        when(userService.findByEmail("test")).thenReturn(user);
        MvcResult page = mvc.perform(get("/games").servletPath("/games").session(session("ROLE_USER")))
                .andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString();
        int modal = html.indexOf("id=\"gameClaimModal\"");
        int advertisement = html.indexOf("data-aa=\"2462874\"", modal);
        int claim = html.indexOf("id=\"gameClaimButton\"");
        assertTrue(modal >= 0 && advertisement > modal && claim > advertisement);
        assertTrue(html.contains("https://acceptable.a-ads.com/2462874/?size=Adaptive"));
        int firstGame = html.indexOf("BubbleBubble/index.html");
        int firstRowEnd = html.indexOf("SoldierAttack3/index.html");
        int betweenRows = html.indexOf("id=\"frame\"");
        int secondRowStart = html.indexOf("NeonMath/index.html");
        assertTrue(firstGame >= 0 && firstGame < firstRowEnd && firstRowEnd < betweenRows
                && betweenRows < secondRowStart);
        assertTrue(html.contains(".game-embed iframe"));
        assertTrue(html.contains("every hour you can claim 50 coins"));
        assertTrue(html.contains("Claim 50 coins"));
        assertTrue(html.contains("name=\"_csrf\""));
        assertTrue(html.contains("name=\"_csrf_header\""));
        assertTrue(html.contains("z-index: 1040;"));
        assertTrue(html.split("class=\"game-embed\"", -1).length == 5);
        assertTrue(html.contains("data-ad-slot=\"2465731755\""));
        assertTrue(html.contains("adsbygoogle.js?client=ca-pub-3177939731250769"));
    }
}
