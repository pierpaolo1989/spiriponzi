package com.soa.spiriponzi.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.config.CaptchaSettings;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, AuthController.class, WihdrawController.class})
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
    void faviconIsPubliclyAccessibleAndLoginPageReferencesIt() throws Exception {
        mvc.perform(get("/img/favicon.ico").servletPath("/img/favicon.ico"))
                .andExpect(status().isOk());
        MvcResult page = mvc.perform(get("/login").servletPath("/login"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(page.getResponse().getContentAsString().contains("href=\"/img/favicon.ico\""));
        org.junit.jupiter.api.Assertions.assertFalse(page.getResponse().getContentAsString().contains("Sign in with Google"));
    }
}
