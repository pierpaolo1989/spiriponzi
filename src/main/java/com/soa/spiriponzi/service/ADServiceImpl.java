package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.Ad;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ADServiceImpl implements ADService {

	private final UserAdRepository userAdRepository;
	private final AdRepository adRepository;

	@Override
	public List<UserADDto> findSurfedAdsByUser(Long userId) {
		var ads = userAdRepository.findByUserIdAndCompletedAtIsNotNull(userId);
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
		adRepository.save(model);
	}

	@Override
	public List<ADDto> getProposedAds(Long userId) {
		var ads = adRepository.findByProposerId(userId);
		return ads.stream().map(ad -> {
			ADDto dto = new ADDto();
			dto.setTitle(ad.getTitle());
			dto.setTimer(ad.getTimer());
			dto.setReward(ad.getReward());
			dto.setUrl(ad.getUrl());
			return dto;
		}).toList();

	}
}