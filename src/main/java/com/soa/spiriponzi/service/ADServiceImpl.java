package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.exception.GenericSpriponziException;
import com.soa.spiriponzi.exception.ResourceNotFoundException;
import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
	public void create(User user, ADDto ad) {
		Ad model = new Ad();
		model.setUrl(ad.getUrl());
		model.setTitle(ad.getTitle());
		model.setReward(ad.getReward());
		model.setTimer(ad.getTimer());
		model.setProposer(user);
		model.setActive(false);
		model.setCreatedAt(LocalDate.now());
		adRepository.save(model);
	}

	@Override
	public void delete(User user, Long id) {
		Optional<Ad> optional = adRepository.findById(id);
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
	public boolean toggleActive(User user, Long id) {
		Ad ad = adRepository.findById(id)
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
		Ad ad = adRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ad not found"));
		if (Objects.isNull(ad.getProposer()) || !ad.getProposer().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operation not admitted");
		}
		var userStats = userStatsRepository.findWithLockByUserEmail(user.getEmail())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no coin balance"));
		double reward = ad.getReward();
		if (!Double.isFinite(reward) || reward <= 0 || !Double.isFinite(budget)) {
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