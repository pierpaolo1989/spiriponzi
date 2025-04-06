package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.UserADDto;
import com.soa.spiriponzi.model.User;

import java.util.List;

public interface ADService {

    List<UserADDto> findSurfedAdsByUser(Long userId);
    void create(User user, ADDto ad);
    List<ADDto> getProposedAds(Long id);
}
