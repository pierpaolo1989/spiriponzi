package com.soa.spiriponzi.service;

import java.util.List;

import com.soa.spiriponzi.dto.ADDto;
import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;

public interface UserService {

	void saveUser(UserDto userDto);

	void updateUser(UpdateUserDto userDto);

	User findByEmail(String email);

	UserStats findStatsByEmail(String email);

	List<UserDto> findAllUsers();

	void recoverPassword(RecoverPasswordDto password);

	List<ADDto> findAdsByUser(Long userId);

	void createUserAd(User user, Long idAd);

}