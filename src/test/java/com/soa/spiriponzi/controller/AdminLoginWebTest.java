package com.soa.spiriponzi.controller;

import jakarta.servlet.RequestDispatcher;
import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.security.CustomUserDetailsService;
import com.soa.spiriponzi.service.CloudflareAnalyticsService;
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

@WebMvcTest(value = AdminController.class, properties = {
        "app.admin.username=test-admin",
        "app.admin.password=local-test-password",
        "cloudflare.web-analytics.token=test-cloudflare-token"
})
@Import({SecurityConfig.class, CustomUserDetailsService.class})
class AdminLoginWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository users;
    @MockBean private AdRepository ads;
    @MockBean private ClaimRepository claims;
    @MockBean private WithdrawRepository withdrawals;
    @MockBean private WithdrawServiceImpl withdrawService;
    @MockBean private RenderService renderService;
    @MockBean private CloudflareAnalyticsService cloudflareAnalyticsService;

    @Test
    void repeatedLoginsWithSeparateSessionsReachAdminDashboard() throws Exception {
        when(withdrawals.findByStatusInOrderByDateRequestAsc(List.of("PENDING"), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of()));
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = mvc.perform(post("/login").servletPath("/login")
                            .param("username", "test-admin").param("password", "local-test-password"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin")).andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            mvc.perform(get("/admin").servletPath("/admin").session(session))
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
        var login = mvc.perform(post("/login").servletPath("/login")
                        .param("username", "test-admin").param("password", "local-test-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

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
                        List.of(new CloudflareAnalyticsService.CountryPageLoads("IT", 15))));
        var login = mvc.perform(post("/login").servletPath("/login")
                        .param("username", "test-admin").param("password", "local-test-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/admin/cloudflare/analytics").servletPath("/admin/cloudflare/analytics").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().json("""
                        {"pageLoads":15,"countries":[{"country":"IT","pageLoads":15}]}
                        """));
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
