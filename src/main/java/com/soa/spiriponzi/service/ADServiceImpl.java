package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.exception.GenericSpriponziException;
import com.soa.spiriponzi.exception.ResourceNotFoundException;
import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ADServiceImpl implements ADService {

	private final UserAdRepository userAdRepository;
	private final AdRepository adRepository;
	private final UserStatsRepository userStatsRepository;

	@Override
	public List<UserADDto> findSurfedAdsByUser(Long userId) {
		var ads = userAdRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId);
		return ads.stream().map(userAd -> {
			UserADDto dto = new UserADDto();
			dto.setTitle(userAd.getAd().getTitle());
			dto.setCompletedAt(userAd.getCompletedAt());
			dto.setReward(userAd.getAd().getReward());
			return dto;
		}).toList();
	}

	@Override
	@Transactional
	public void create(User user, ADDto ad) {
		if (ad == null || ad.getTitle() == null || ad.getTitle().isBlank()
				|| ad.getTitle().trim().length() > 150 || ad.getTimer() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad title and visit duration are invalid");
		}
		String title = ad.getTitle().trim();
		String url = validateAdUrl(ad.getUrl());

		UserStats userStats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no coin balance"));
		int budgetAmount = validateInitialBudget(ad.getReward(), ad.getBudget(), userStats.getAnnualPoints());

		Ad model = new Ad();
		model.setUrl(url);
		model.setTitle(title);
		model.setReward(ad.getReward());
		model.setBudget(budgetAmount);
		model.setTimer(ad.getTimer());
		model.setProposer(user);
		model.setActive(false);
		model.setCreatedAt(LocalDate.now());
		userStats.setAnnualPoints(userStats.getAnnualPoints() - budgetAmount);
		adRepository.save(model);
		userStatsRepository.save(userStats);
	}

	private String validateAdUrl(String value) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad URL must be an HTTP or HTTPS address");
		}
		String url = value.trim();
		try {
			URI destination = URI.create(url);
			String scheme = destination.getScheme();
			if (destination.getHost() == null
					|| !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad URL must be an HTTP or HTTPS address");
			}
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad URL must be an HTTP or HTTPS address", ex);
		}
		return url;
	}

	private int validateInitialBudget(double reward, double budget, int availablePoints) {
		if (!Double.isFinite(reward) || reward <= 0 || reward > Integer.MAX_VALUE
				|| BigDecimal.valueOf(reward).stripTrailingZeros().scale() > 0
				|| !Double.isFinite(budget)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Budget must be a positive whole-number multiple of the reward and within your available balance");
		}
		BigDecimal rewardAmount = BigDecimal.valueOf(reward);
		BigDecimal budgetAmount = BigDecimal.valueOf(budget);
		if (budgetAmount.compareTo(rewardAmount) < 0
				|| budgetAmount.remainder(rewardAmount).compareTo(BigDecimal.ZERO) != 0
				|| budgetAmount.stripTrailingZeros().scale() > 0
				|| budgetAmount.compareTo(BigDecimal.valueOf(availablePoints)) > 0) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Budget must be a positive whole-number multiple of the reward and within your available balance");
		}
		return budgetAmount.intValueExact();
	}

	@Override
	@Transactional
	public void delete(User user, Long id) {
		Optional<Ad> optional = adRepository.findWithLockById(id);
		if (optional.isPresent()) {
			var ad = optional.get();
			if (Objects.isNull(ad.getProposer()) || !ad.getProposer().getId().equals(user.getId())) {
				throw new GenericSpriponziException(HttpStatus.FORBIDDEN.name(), "Operation not admitted");
			}
			ad.setDeleted(true);
			adRepository.save(ad);
		} else {
			throw new ResourceNotFoundException(HttpStatus.NOT_FOUND.name(), "Resource not found");
		}
	}

	@Override
	@Transactional
	public boolean toggleActive(User user, Long id) {
		Ad ad = adRepository.findWithLockById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ad not found"));
		if (Objects.isNull(ad.getProposer()) || !ad.getProposer().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operation not admitted");
		}

		ad.setActive(!ad.isActive());
		adRepository.save(ad);
		return ad.isActive();
	}

	@Override
	public double getAvailableBalance(User user) {
		return user.getUserStats() == null ? 0 : user.getUserStats().getAnnualPoints();
	}

	@Override
	@Transactional
	public double updateBudget(User user, Long id, double budget) {
		Ad ad = adRepository.findWithLockById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ad not found"));
		if (Objects.isNull(ad.getProposer()) || !ad.getProposer().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operation not admitted");
		}
		var userStats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no coin balance"));
		double reward = ad.getReward();
		if (!Double.isFinite(reward) || reward <= 0
				|| BigDecimal.valueOf(reward).stripTrailingZeros().scale() > 0
				|| !Double.isFinite(budget)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Budget must be a multiple of the ad reward and no greater than your available balance"
			);
		}
		BigDecimal budgetChange = BigDecimal.valueOf(budget).subtract(BigDecimal.valueOf(ad.getBudget()));
		BigDecimal availableBalance = BigDecimal.valueOf(userStats.getAnnualPoints());
		if (budget < reward
				|| BigDecimal.valueOf(budget).remainder(BigDecimal.valueOf(reward)).compareTo(BigDecimal.ZERO) != 0
				|| budgetChange.compareTo(availableBalance) > 0
				|| budgetChange.stripTrailingZeros().scale() > 0) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Budget must be a multiple of the ad reward and no greater than your available balance"
			);
		}

		userStats.setAnnualPoints(userStats.getAnnualPoints() - budgetChange.intValueExact());
		userStatsRepository.save(userStats);
		ad.setBudget(budget);
		adRepository.save(ad);
		return userStats.getAnnualPoints();
	}

	@Override
	public List<ADDto> getProposedAds(Long userId) {
		var ads = adRepository.findByProposerIdAndDeletedIsFalse(userId);
		return ads.stream().map(ad -> {
			ADDto dto = new ADDto();
			dto.setId(ad.getId());
			dto.setTitle(ad.getTitle());
			dto.setTimer(ad.getTimer());
			dto.setReward(ad.getReward());
			dto.setBudget(ad.getBudget());
			dto.setCreatedAt(ad.getCreatedAt());
			dto.setUrl(ad.getUrl());
			dto.setActive(ad.isActive());
			return dto;
		}).toList();

	}

	@Override
	@Transactional
	public void reportAbuse(Long id) {
		Ad ad = adRepository.findWithLockById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ad not found"));
		ad.setAbuseCount(ad.getAbuseCount() + 1);
		adRepository.save(ad);
	}
}