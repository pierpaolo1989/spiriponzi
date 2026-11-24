package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
import com.soa.spiriponzi.security.CustomUserDetailsService;
import com.soa.spiriponzi.service.RenderService;
import com.soa.spiriponzi.service.WithdrawServiceImpl;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(content().string(containsString("test-cloudflare-token")));
    }
}
