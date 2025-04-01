package com.soa.spiriponzi.service;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.soa.spiriponzi.dto.RecoverPasswordDto;
import com.soa.spiriponzi.dto.ResetPasswordDto;
import com.soa.spiriponzi.dto.UpdateUserDto;
import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.Role;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.RoleRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public void saveUser(UserDto userDto) {
        User user = new User();
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        Role role = roleRepository.findByName("ROLE_USER");
        if (role == null){
            role = checkRoleExist();
        }
        user.setRoles(Arrays.asList(role));
        //User stats
        var userStats = new UserStats();
        userStats.setNumberOfClaims(1);
        userStats.setMonthlyPoints(0);
        userStats.setAnnualPoints(0);
        userStatsRepository.save(userStats);
        user.setUserStats(userStats);
        userRepository.save(user);
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
        return users.stream().map((user) -> convertEntityToDto(user))
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
	public void recoverPassword(RecoverPasswordDto password) {
		log.info("Sending email for password procedure. User with email: {}", password.getEmail());
		ResetPasswordDto reset = ResetPasswordDto.builder().email(password.getEmail()).build();
		try {
			emailService.sendEmail(reset);
		} catch (Exception e) {
			log.error("Impossible to send email. Error: {}", e.getMessage(), e);
		}
		
	}
}