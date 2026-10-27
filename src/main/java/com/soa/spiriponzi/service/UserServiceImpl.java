package com.soa.spiriponzi.service;


import com.soa.spiriponzi.dto.*;
import com.soa.spiriponzi.exception.ClaimException;
import com.soa.spiriponzi.model.*;
import com.soa.spiriponzi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AdRepository adRepository;
    private final UserAdRepository userAdRepository;

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
        return users.stream().map(this::convertEntityToDto)
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

    @Override
    public List<ADDto> findAdsByUser(Long userId) {
        List<Object[]> results = adRepository.findAdsWithCompletionStatus(userId);

        return results.stream()
                .map(row -> {
                    Ad ad = (Ad) row[0];
                    boolean completed = (Boolean) row[1];
                    ADDto dto = new ADDto();
                    dto.setId(ad.getId());
                    dto.setUrl(ad.getUrl());
                    dto.setTitle(ad.getTitle());
                    dto.setTimer(ad.getTimer());
                    dto.setReward(ad.getReward());
                    dto.setCompleted(completed);
                    dto.setActive(ad.isActive());
                    return dto;
                })
                .toList();
    }

    @Override
    public void createUserAd(User user, Long idAd) {
        var ad = adRepository.findById(idAd)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ad not found"));
        if (!ad.isActive() || ad.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad is not available");
        }
        if (ad.getProposer() != null && ad.getProposer().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot surf your own ad");
        }
        UserAd userAd = new UserAd();
        userAd.setAd(ad);
        userAd.setUser(user);
        userAd.setCompletedAt(LocalDate.now());
        userAdRepository.save(userAd);

        //Update user stats
        Optional<UserStats> optUserStats = userStatsRepository.findByUserEmail(user.getEmail());
        UserStats userStats = optUserStats.get();
        int amount = (int) ad.getReward();
        if (userStats.getLastRequest().getMonth() == LocalDate.now().getMonth()) {
            userStats.setMonthlyPoints(userStats.getMonthlyPoints() + amount);
        } else {
            userStats.setMonthlyPoints((int) ad.getReward());
        }
        userStats.setAnnualPoints(userStats.getAnnualPoints() + amount);
        userStatsRepository.save(userStats);
    }
}