package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.exception.GenericSpriponziException;
import com.soa.spiriponzi.exception.ResourceNotFoundException;
import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ADServiceImpl implements ADService {

	private final UserAdRepository userAdRepository;
	private final AdRepository adRepository;

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
	public List<ADDto> getProposedAds(Long userId) {
		var ads = adRepository.findByProposerIdAndDeletedIsFalse(userId);
		return ads.stream().map(ad -> {
			ADDto dto = new ADDto();
			dto.setId(ad.getId());
			dto.setTitle(ad.getTitle());
			dto.setTimer(ad.getTimer());
			dto.setReward(ad.getReward());
			dto.setCreatedAt(ad.getCreatedAt());
			dto.setUrl(ad.getUrl());
			return dto;
		}).toList();

	}
}