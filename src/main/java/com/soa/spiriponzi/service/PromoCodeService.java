package com.soa.spiriponzi.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.soa.spiriponzi.model.PromoCode;
import com.soa.spiriponzi.model.PromoCodeRedemption;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.PromoCodeRedemptionRepository;
import com.soa.spiriponzi.repository.PromoCodeRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromoCodeService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 16;
    private static final int CODE_GENERATION_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PromoCode> listCodes() {
        return promoCodeRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public PromoCode createCode(int points, int maxRedemptions, LocalDate expiresOn) {
        LocalDate today = LocalDate.now(clock);
        if (points <= 0 || maxRedemptions <= 0 || expiresOn == null || expiresOn.isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Punti, utilizzi e data di scadenza devono essere validi.");
        }

        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (!promoCodeRepository.existsByCode(code)) {
                PromoCode promoCode = new PromoCode();
                promoCode.setCode(code);
                promoCode.setPoints(points);
                promoCode.setMaxRedemptions(maxRedemptions);
                promoCode.setRedemptionsUsed(0);
                promoCode.setExpiresOn(expiresOn);
                promoCode.setCreatedAt(LocalDateTime.now(clock));
                return promoCodeRepository.save(promoCode);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossibile generare un codice univoco.");
    }

    @Transactional
    public int redeemCode(String submittedCode, String email) {
        String code = submittedCode == null ? "" : submittedCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inserisci un codice promozionale.");
        }

        PromoCode promoCode = promoCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codice non valido."));
        if (LocalDate.now(clock).isAfter(promoCode.getExpiresOn())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Questo codice promozionale è scaduto.");
        }
        if (promoCode.getRedemptionsUsed() >= promoCode.getMaxRedemptions()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Questo codice ha raggiunto il limite di utilizzi.");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utente non trovato.");
        }
        if (redemptionRepository.existsByPromoCode_IdAndUser_Id(promoCode.getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hai già riscattato questo codice.");
        }

        UserStats stats = userStatsRepository.findWithLockByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo punti non disponibile."));
        try {
            stats.setAnnualPoints(Math.addExact(stats.getAnnualPoints(), promoCode.getPoints()));
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Il saldo punti ha raggiunto il limite.", ex);
        }

        promoCode.setRedemptionsUsed(promoCode.getRedemptionsUsed() + 1);
        PromoCodeRedemption redemption = new PromoCodeRedemption();
        redemption.setPromoCode(promoCode);
        redemption.setUser(user);
        redemption.setRedeemedAt(LocalDateTime.now(clock));
        userStatsRepository.save(stats);
        promoCodeRepository.save(promoCode);
        redemptionRepository.save(redemption);
        return promoCode.getPoints();
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder("SP-");
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
