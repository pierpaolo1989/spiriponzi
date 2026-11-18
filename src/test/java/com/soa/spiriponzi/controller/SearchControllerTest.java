package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchControllerTest {

    private UserService userService;
    private SearchController controller;
    private final User user = User.builder().id(12L).email(ControllerTestSupport.EMAIL).build();

    @BeforeEach
    void setUp() {
        ControllerTestSupport.authenticate();
        userService = mock(UserService.class);
        controller = new SearchController(userService);
        when(userService.findByEmail(ControllerTestSupport.EMAIL)).thenReturn(user);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void searchReturnsMatchingPagesAndAdsCaseInsensitively() {
        when(userService.findAdsByUser(12L)).thenReturn(List.of(
                ADDto.builder().id(7L).title("Academy bitcoin offer").reward(15).timer(20).build(),
                ADDto.builder().id(8L).title("Ethereum offer").build()
        ));
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("search", controller.search("  ACADEMY ", model));

        assertEquals("ACADEMY", model.get("query"));
        assertEquals(1, ((List<?>) model.get("pageResults")).size());
        var adResults = (List<?>) model.get("adResults");
        assertEquals(1, adResults.size());
        assertEquals(2, model.get("resultCount"));
        var adResult = (com.soa.spiriponzi.dto.SearchResultDto) adResults.get(0);
        assertEquals("Academy bitcoin offer", adResult.getTitle());
        assertEquals("/surf-ads#ad-7", adResult.getUrl());
        verify(userService).findByEmail(ControllerTestSupport.EMAIL);
        verify(userService).findAdsByUser(12L);
    }

    @Test
    void blankQueryReturnsNoResults() {
        when(userService.findAdsByUser(12L)).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.search(" ", model);

        assertEquals("", model.get("query"));
        assertEquals(0, model.get("resultCount"));
        assertEquals(List.of(), model.get("pageResults"));
        assertEquals(List.of(), model.get("adResults"));
    }

    @Test
    void offerwallIsSearchable() {
        when(userService.findAdsByUser(12L)).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.search("offerwall", model);

        var pageResults = (List<?>) model.get("pageResults");
        assertEquals(1, pageResults.size());
        assertEquals("Offerwall", ((com.soa.spiriponzi.dto.SearchResultDto) pageResults.get(0)).getTitle());
        assertEquals("/offerwall", ((com.soa.spiriponzi.dto.SearchResultDto) pageResults.get(0)).getUrl());
    }
}
