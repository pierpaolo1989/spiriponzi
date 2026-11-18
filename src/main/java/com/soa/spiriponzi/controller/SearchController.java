package com.soa.spiriponzi.controller;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.SearchResultDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private static final List<SearchResultDto> APP_PAGES = List.of(
            new SearchResultDto("Dashboard", "Claim coins and check your account overview.", "Page", "/dashboard"),
            new SearchResultDto("Surf Ads", "View available ads and earn points.", "Page", "/surf-ads"),
            new SearchResultDto("Create Ads", "Create and manage your own ads.", "Page", "/create-ads"),
            new SearchResultDto("Surveys", "Find surveys available through our partners.", "Page", "/survey"),
            new SearchResultDto("Offerwall", "Complete partner offers and earn points.", "Page", "/offerwall"),
            new SearchResultDto("Withdraw", "Review withdrawal options and requests.", "Page", "/withdraw-requests"),
            new SearchResultDto("Reports", "Review your activity and earnings.", "Page", "/report"),
            new SearchResultDto("Academy", "Learn about cryptocurrency and blockchain.", "Page", "/academy"),
            new SearchResultDto("Games", "Play games and earn coins.", "Page", "/games"),
            new SearchResultDto("Profile", "Manage your account and wallet details.", "Page", "/profile"),
            new SearchResultDto("FAQ", "Find answers to frequently asked questions.", "Page", "/faq")
    );

    private final UserService userService;

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", defaultValue = "") String query, Model model) {
        String normalizedQuery = query.trim();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByEmail(authentication.getName());

        List<SearchResultDto> pageResults = APP_PAGES.stream()
                .filter(page -> matches(page.getTitle(), page.getDescription(), normalizedQuery))
                .toList();
        List<SearchResultDto> adResults = userService.findAdsByUser(user.getId()).stream()
                .filter(ad -> matches(ad.getTitle(), normalizedQuery))
                .map(this::toSearchResult)
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("query", normalizedQuery);
        model.addAttribute("pageResults", pageResults);
        model.addAttribute("adResults", adResults);
        model.addAttribute("resultCount", pageResults.size() + adResults.size());
        return "search";
    }

    private boolean matches(String title, String description, String query) {
        return !query.isBlank()
                && (containsIgnoreCase(title, query) || containsIgnoreCase(description, query));
    }

    private boolean matches(String title, String query) {
        return !query.isBlank() && containsIgnoreCase(title, query);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private SearchResultDto toSearchResult(ADDto ad) {
        String description = "Reward: " + ad.getReward() + " points · " + ad.getTimer() + " seconds";
        return new SearchResultDto(ad.getTitle(), description, "Surf ad", "/surf-ads#ad-" + ad.getId());
    }
}
