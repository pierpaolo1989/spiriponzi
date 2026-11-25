package com.soa.spiriponzi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.soa.spiriponzi.model.AffiliateClick;
import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AffiliateClickRepository;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffiliateService {

    public static final String VISITOR_COOKIE_NAME = "spiriponzi_affiliate_visitor";
    public static final int CLICKS_PER_REWARD = 100;
    public static final int REWARD_COINS = 10;

    private static final long VISITOR_CLICK_INTERVAL_HOURS = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AffiliateClickRepository affiliateClickRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional(readOnly = true)
    public AffiliateStats getStats(Long userId) {
        long clicks = affiliateClickRepository.countByAffiliateUser_Id(userId);
        return new AffiliateStats(clicks, clicks / CLICKS_PER_REWARD * REWARD_COINS,
                CLICKS_PER_REWARD - clicks % CLICKS_PER_REWARD);
    }

    @Transactional
    public ClickResult trackClick(String referralCode, String existingVisitorToken, String currentUserEmail) {
        User affiliate = userRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Affiliate code not found"));

        boolean setVisitorCookie = !isVisitorTokenValid(existingVisitorToken);
        String visitorToken = setVisitorCookie ? createVisitorToken() : existingVisitorToken;

        if (affiliate.getEmail().equalsIgnoreCase(currentUserEmail == null ? "" : currentUserEmail)) {
            return new ClickResult(visitorToken, setVisitorCookie, false, 0, 0);
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(affiliate.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Affiliate account has no point balance"));
        String visitorHash = hashVisitorToken(visitorToken);
        LocalDateTime now = LocalDateTime.now();
        if (affiliateClickRepository.existsByAffiliateUser_IdAndVisitorHashAndClickedAtAfter(
                affiliate.getId(), visitorHash, now.minusHours(VISITOR_CLICK_INTERVAL_HOURS))) {
            return new ClickResult(visitorToken, setVisitorCookie, false,
                    affiliateClickRepository.countByAffiliateUser_Id(affiliate.getId()), 0);
        }

        long previousClicks = affiliateClickRepository.countByAffiliateUser_Id(affiliate.getId());
        affiliateClickRepository.save(AffiliateClick.builder()
                .affiliateUser(affiliate)
                .visitorHash(visitorHash)
                .clickedAt(now)
                .build());

        long totalClicks = previousClicks + 1;
        int awardedCoins = 0;
        if (totalClicks % CLICKS_PER_REWARD == 0) {
            awardMilestone(affiliate, stats, totalClicks, now);
            awardedCoins = REWARD_COINS;
        }

        return new ClickResult(visitorToken, setVisitorCookie, true, totalClicks, awardedCoins);
    }

    private void awardMilestone(User affiliate, UserStats stats, long totalClicks, LocalDateTime now) {
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), REWARD_COINS));
            stats.setMonthlyPoints(Math.addExact(stats.getMonthlyPoints(), REWARD_COINS));
            stats.setNumberOfClaims(Math.addExact(stats.getNumberOfClaims(), 1));
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Affiliate reward exceeds the coin limit");
        }

        claimRepository.save(Claim.builder()
                .user(affiliate)
                .dateRequest(now)
                .amount(REWARD_COINS)
                .externalTransactionId("affiliate-clicks-" + affiliate.getId() + "-" + totalClicks)
                .build());
        userStatsRepository.save(stats);
    }

    private boolean isVisitorTokenValid(String visitorToken) {
        return visitorToken != null && visitorToken.matches("[A-Za-z0-9_-]{43}");
    }

    private String createVisitorToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashVisitorToken(String visitorToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(visitorToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record AffiliateStats(long qualifiedClicks, long coinsEarned, long clicksUntilReward) {
    }

    public record ClickResult(
            String visitorToken, boolean setVisitorCookie, boolean counted, long totalClicks, int awardedCoins) {
    }
}
