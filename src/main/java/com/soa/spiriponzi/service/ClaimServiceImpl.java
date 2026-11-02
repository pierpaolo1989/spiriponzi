package com.soa.spiriponzi.service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.soa.spiriponzi.dto.ClaimDto;
import com.soa.spiriponzi.dto.UserStatsDto;
import com.soa.spiriponzi.exception.ClaimException;
import com.soa.spiriponzi.model.Claim;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClaimServiceImpl implements ClaimService {

	private static final int GAME_CLAIM_AMOUNT = 100;
	private static final long GAME_CLAIM_INTERVAL_SECONDS = 60;

	private final ClaimRepository claimRepository;
	private final UserRepository userRepository;
	private final UserStatsRepository userStatsRepository;

	@Override
	@Transactional
	public void startClaim(String email) {
		User user = userRepository.findByEmail(email);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}

		UserStats userStats = userStatsRepository.findWithLockByUserEmail(email)
				.orElseGet(() -> {
					UserStats newStats = new UserStats();
					newStats.setUser(user);
					userStatsRepository.save(newStats);
					user.setUserStats(newStats);
					userRepository.save(user);
					return newStats;
				});
		LocalDateTime now = LocalDateTime.now();
		if (userStats.getLastRequest() != null
				&& now.isBefore(userStats.getLastRequest().plusHours(1))) {
			throw new ClaimException("You can claim once every hour");
		}

		int amount = new Random().nextInt(99) + 1;
		Claim claim = Claim.builder()
				.user(user)
				.dateRequest(now)
				.amount(amount)
				.build();

		if (userStats.getLastRequest() == null || isInCurrentMonth(userStats.getLastRequest(), now)) {
			userStats.setMonthlyPoints(userStats.getMonthlyPoints() + amount);
		} else {
			userStats.setMonthlyPoints(amount);
		}
		userStats.setNumberOfClaims(userStats.getNumberOfClaims() + 1);
		userStats.setAnnualPoints(userStats.getAnnualPoints() + amount);
		userStats.setLastRequest(now);

		claimRepository.save(claim);
		userStatsRepository.save(userStats);
	}

	@Override
	@Transactional
	public long getGameClaimCooldownSeconds(String email) {
		UserStats userStats = getOrCreateLockedStats(email);
		LocalDateTime now = LocalDateTime.now();
		if (userStats.getLastGameClaimAt() == null) {
			userStats.setLastGameClaimAt(now);
			userStatsRepository.save(userStats);
		}
		return secondsUntilGameClaim(userStats.getLastGameClaimAt(), now);
	}

	@Override
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public UserStatsDto startGameClaim(String email) {
		UserStats userStats = getOrCreateLockedStats(email);
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime lastGameClaimAt = userStats.getLastGameClaimAt();
		if (lastGameClaimAt == null) {
			userStats.setLastGameClaimAt(now);
			userStatsRepository.save(userStats);
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 60 seconds before claiming game coins");
		}
		if (secondsUntilGameClaim(lastGameClaimAt, now) > 0) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before claiming game coins again");
		}

		User user = userRepository.findByEmail(email);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}

		Claim claim = Claim.builder()
				.user(user)
				.dateRequest(now)
				.amount(GAME_CLAIM_AMOUNT)
				.build();
		claimRepository.save(claim);

		boolean hasPointsThisMonth = isInCurrentMonth(userStats.getLastRequest(), now)
				|| isInCurrentMonth(lastGameClaimAt, now);
		userStats.setMonthlyPoints(hasPointsThisMonth
				? userStats.getMonthlyPoints() + GAME_CLAIM_AMOUNT
				: GAME_CLAIM_AMOUNT);
		userStats.setAnnualPoints(userStats.getAnnualPoints() + GAME_CLAIM_AMOUNT);
		userStats.setNumberOfClaims(userStats.getNumberOfClaims() + 1);
		userStats.setLastGameClaimAt(now);
		userStatsRepository.save(userStats);

		return UserStatsDto.builder()
				.annualPoints(userStats.getAnnualPoints())
				.monthlyPoints(userStats.getMonthlyPoints())
				.numberOfClaims(userStats.getNumberOfClaims())
				.withdrawals(userStats.getWithdrawals())
				.lastRequest(userStats.getLastRequest())
				.build();
	}

	private UserStats getOrCreateLockedStats(String email) {
		Optional<UserStats> existingStats = userStatsRepository.findWithLockByUserEmail(email);
		if (existingStats.isPresent()) {
			return existingStats.get();
		}

		User user = userRepository.findByEmail(email);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}
		UserStats userStats = new UserStats();
		userStats.setUser(user);
		userStats.setLastGameClaimAt(LocalDateTime.now());
		userStatsRepository.save(userStats);
		user.setUserStats(userStats);
		userRepository.save(user);
		return userStats;
	}

	private long secondsUntilGameClaim(LocalDateTime lastGameClaimAt, LocalDateTime now) {
		long millisecondsRemaining = Duration.between(
				now,
				lastGameClaimAt.plusSeconds(GAME_CLAIM_INTERVAL_SECONDS)
		).toMillis();
		return millisecondsRemaining <= 0 ? 0 : (millisecondsRemaining + 999) / 1000;
	}

	private boolean isInCurrentMonth(LocalDateTime date, LocalDateTime now) {
		return date != null && date.getYear() == now.getYear() && date.getMonth() == now.getMonth();
	}

	@Override
	public List<ClaimDto> findByUser(String email) {
		List<Claim> claims = claimRepository.findTop10ByUserEmailOrderByDateRequestDesc(email);
		return claims.stream().map(obj -> {
			return ClaimDto.builder()
					.amount(obj.getAmount())
					.date(obj.getDateRequest())
					.id(obj.getId())
					.build();
		}).collect(Collectors.toList());
	}

}