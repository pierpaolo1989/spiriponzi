package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.config.SecurityConfig;
import com.soa.spiriponzi.dto.UserStatsDto;
import com.soa.spiriponzi.service.CaptchaService;
import com.soa.spiriponzi.service.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import(SecurityConfig.class)
class GameClaimWebTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private CaptchaService captchaService;
    @MockBean private ClaimService claimService;

    @Test
    void claimRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(post("/claims/game").servletPath("/claims/game").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/claims/game").servletPath("/claims/game").with(user("player").roles("USER")))
                .andExpect(status().isForbidden());
        verify(claimService, never()).startGameClaim("player");
    }

    @Test
    void claimUsesAuthenticatedAccountAndRejectsCooldown() throws Exception {
        when(claimService.startGameClaim("player")).thenReturn(UserStatsDto.builder().annualPoints(50).build())
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before claiming"));

        var request = post("/claims/game").servletPath("/claims/game")
                .with(user("player").roles("USER")).with(csrf().asHeader())
                .param("email", "other").param("amount", "999999");
        mvc.perform(request).andExpect(status().isOk());
        mvc.perform(request).andExpect(status().isTooManyRequests());
        verify(claimService, never()).startGameClaim("other");
    }
}
