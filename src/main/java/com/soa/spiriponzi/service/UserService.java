package com.soa.spiriponzi.service;

import java.util.List;

import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.User;

public interface UserService {
    
	void saveUser(UserDto userDto);
	
	void updateUser(UpdateUserDto userDto);

    User findByEmail(String email);

    List<UserDto> findAllUsers();
    
}