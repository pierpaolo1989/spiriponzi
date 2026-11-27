package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.service.OfferwallService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EarnwallPostbackControllerTest {

    @Test
    void acceptsEarnwallFormPostAndReturnsProviderAcknowledgement() throws Exception {
        OfferwallService service = mock(OfferwallService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EarnwallPostbackController(service)).build();

        mvc.perform(post("/earnwall/postback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("subId", "10")
                        .param("transId", "tx-1")
                        .param("reward", "15.9")
                        .param("status", "1")
                        .param("debug", "0")
                        .param("signature", "signed-value"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(service).processEarnwallCallback("10", "tx-1", "15.9", "1", "0", "signed-value");
    }
}
