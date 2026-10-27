package com.soa.spiriponzi.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.soa.spiriponzi.dto.ClaimDto;
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

	private final ClaimRepository claimRepository;
	private final UserRepository userRepository;
	private final UserStatsRepository userStatsRepository;

	@Override
	public void startClaim(String email) {
		User user = userRepository.findByEmail(email);
		var amount = new Random().nextInt(99) + 1;
		var now = LocalDateTime.now();
		var claim = Claim.builder()
				.user(user)
				.dateRequest(LocalDateTime.now())
				.amount(amount)
				.build();
		claimRepository.save(claim);
		
		//Update stats --> transform in async
		Optional<UserStats> optUserStats = userStatsRepository.findByUserEmail(email);
		if (optUserStats.isEmpty()) {
			var userStats = new UserStats();
			userStats.setNumberOfClaims(1);
			userStats.setMonthlyPoints(amount);
			userStats.setAnnualPoints(amount);
			userStats.setLastRequest(claim.getDateRequest());
			userStatsRepository.save(userStats);
			user.setUserStats(userStats);
			userRepository.save(user);
			
		} else {
			UserStats userStats = optUserStats.get();
			if (Objects.isNull(userStats.getLastRequest())) {
				userStats.setLastRequest(claim.getDateRequest());
				userStatsRepository.save(userStats);
				return;
			}
			if (now.isBefore(userStats.getLastRequest().plusHours(1))) {
				throw new ClaimException();
			}
			if (userStats.getLastRequest().getMonth() == claim.getDateRequest().getMonth()) {
				userStats.setMonthlyPoints(userStats.getMonthlyPoints() + amount);
			} else {
				userStats.setMonthlyPoints(amount);
			}
			userStats.setNumberOfClaims(userStats.getNumberOfClaims() + 1);
			userStats.setAnnualPoints(userStats.getAnnualPoints() + amount);
			userStats.setLastRequest(claim.getDateRequest());
			userStatsRepository.save(userStats);
		}
		
		
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