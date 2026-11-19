package com.soa.spiriponzi.service;


import com.soa.spiriponzi.dto.*;
import com.soa.spiriponzi.exception.ClaimException;
import com.soa.spiriponzi.model.*;
import com.soa.spiriponzi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private static final int REFERRAL_REWARD = 1000;
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserStatsRepository userStatsRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AdRepository adRepository;
    private final UserAdRepository userAdRepository;

    @Value("${app.public-base-url:https://www.spiriponzi.cloud}")
    private String publicBaseUrl;

    @Override
    @Transactional
    public void saveUser(UserDto userDto) {
        User referrer = findReferrer(userDto.getReferralCode());
        User user = new User();
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setReferralCode(generateReferralCode());
        user.setReferredBy(referrer);
        Role role = roleRepository.findByName("ROLE_USER");
        if (role == null){
            role = checkRoleExist();
        }
        user.setRoles(Arrays.asList(role));
        //User stats
        var userStats = new UserStats();
        userStats.setNumberOfClaims(0);
        userStats.setMonthlyPoints(referrer == null ? 0 : REFERRAL_REWARD);
        userStats.setAnnualPoints(referrer == null ? 0 : REFERRAL_REWARD);
        userStatsRepository.save(userStats);
        user.setUserStats(userStats);
        userRepository.save(user);

        if (referrer != null) {
            UserStats referrerStats = userStatsRepository.findWithLockByUserEmail(referrer.getEmail())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Referrer has no coin balance"
                    ));
            referrerStats.setAnnualPoints(referrerStats.getAnnualPoints() + REFERRAL_REWARD);
            referrerStats.setMonthlyPoints(referrerStats.getMonthlyPoints() + REFERRAL_REWARD);
            userStatsRepository.save(referrerStats);
        }
    }

    @Override
    public boolean isValidReferralCode(String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return true;
        }
        return userRepository.findByReferralCode(normalizeReferralCode(referralCode)).isPresent();
    }

    @Override
    @Transactional
    public String getOrCreateReferralCode(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getReferralCode() == null || user.getReferralCode().isBlank()) {
            user.setReferralCode(generateReferralCode());
            userRepository.save(user);
        }
        return user.getReferralCode();
    }

    private User findReferrer(String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return null;
        }
        return userRepository.findByReferralCode(normalizeReferralCode(referralCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid referral code"));
    }

    private String normalizeReferralCode(String referralCode) {
        return referralCode.trim().toUpperCase(Locale.ROOT);
    }

    private String generateReferralCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
            if (!userRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique referral code");
    }
    
    @Override
    public void updateUser(UpdateUserDto userDto) {
        var user = userRepository.findByEmail(userDto.getEmail());
        user.setBtcWallet(userDto.getBtcWallet());
        user.setEthWallet(userDto.getEthWallet());
        userRepository.save(user);
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public List<UserDto> findAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(this::convertEntityToDto)
                .collect(Collectors.toList());
    }

    private UserDto convertEntityToDto(User user){
        UserDto userDto = new UserDto();
        userDto.setEmail(user.getEmail());
        return userDto;
    }

    private Role checkRoleExist() {
        Role role = new Role();
        role.setName("ROLE_USER");
        return roleRepository.save(role);
    }

	@Override
	public UserStats findStatsByEmail(String email) {
		return userStatsRepository.findByUserEmail(email).orElse(null);
	}

	@Override
	@Transactional
	public void recoverPassword(RecoverPasswordDto password) {
		User user = userRepository.findByEmail(password.getEmail().trim());
		if (user == null) {
			return;
		}

		byte[] randomBytes = new byte[32];
		SECURE_RANDOM.nextBytes(randomBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setTokenHash(hashPasswordResetToken(token));
		resetToken.setUser(user);
		resetToken.setExpiresAt(LocalDateTime.now().plus(PASSWORD_RESET_TOKEN_TTL));

		passwordResetTokenRepository.deleteAllByUserId(user.getId());
		passwordResetTokenRepository.save(resetToken);

		String resetUrl = UriComponentsBuilder.fromUriString(publicBaseUrl)
				.path("/reset-password")
				.queryParam("token", token)
				.build()
				.toUriString();
		try {
			emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
		} catch (jakarta.mail.MessagingException | java.io.IOException | freemarker.template.TemplateException e) {
			throw new IllegalStateException("Unable to send password reset email", e);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isPasswordResetTokenValid(String token) {
		if (token == null || token.isBlank()) {
			return false;
		}
		return passwordResetTokenRepository.findByTokenHash(hashPasswordResetToken(token))
				.filter(resetToken -> resetToken.getExpiresAt().isAfter(LocalDateTime.now()))
				.isPresent();
	}

	@Override
	@Transactional
	public boolean resetPassword(String token, String newPassword) {
		if (token == null || token.isBlank()) {
			return false;
		}

		PasswordResetToken resetToken = passwordResetTokenRepository
				.findWithLockByTokenHash(hashPasswordResetToken(token))
				.orElse(null);
		if (resetToken == null) {
			return false;
		}
		if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
			passwordResetTokenRepository.delete(resetToken);
			return false;
		}

		User user = resetToken.getUser();
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		passwordResetTokenRepository.deleteAllByUserId(user.getId());
		return true;
	}

	private String hashPasswordResetToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

    @Override
    public List<ADDto> findAdsByUser(Long userId) {
        List<Object[]> results = adRepository.findAdsWithCompletionStatus(userId);

        return results.stream()
                .map(row -> {
                    Ad ad = (Ad) row[0];
                    boolean completed = (Boolean) row[1];
                    ADDto dto = new ADDto();
                    dto.setId(ad.getId());
                    dto.setUrl(ad.getUrl());
                    dto.setTitle(ad.getTitle());
                    dto.setTimer(ad.getTimer());
                    dto.setReward(ad.getReward());
                    dto.setCompleted(completed);
                    dto.setActive(ad.isActive());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional
    public void createUserAd(User user, Long idAd) {
        var ad = adRepository.findById(idAd)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ad not found"));
        if (!ad.isActive() || ad.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad is not available");
        }
        if (ad.getProposer() != null && ad.getProposer().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot surf your own ad");
        }
        UserAd userAd = new UserAd();
        userAd.setAd(ad);
        userAd.setUser(user);
        userAd.setCompletedAt(LocalDate.now());
        userAdRepository.save(userAd);

        //Update user stats
        UserStats userStats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "User has no point balance"));
        int amount = (int) ad.getReward();
        LocalDateTime lastRequest = userStats.getLastRequest();
        LocalDate today = LocalDate.now();
        if (lastRequest == null
                || (lastRequest.getYear() == today.getYear() && lastRequest.getMonth() == today.getMonth())) {
            userStats.setMonthlyPoints(userStats.getMonthlyPoints() + amount);
        } else {
            userStats.setMonthlyPoints(amount);
        }
        userStats.setAnnualPoints(userStats.getAnnualPoints() + amount);
        userStatsRepository.save(userStats);
    }
}