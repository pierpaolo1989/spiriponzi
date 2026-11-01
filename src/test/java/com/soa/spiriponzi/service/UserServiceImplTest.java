package com.soa.spiriponzi.service;

import com.soa.spiriponzi.dto.UserDto;
import com.soa.spiriponzi.model.Role;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.AdRepository;
import com.soa.spiriponzi.repository.RoleRepository;
import com.soa.spiriponzi.repository.UserAdRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserRepository userRepository;
    private UserStatsRepository userStatsRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userStatsRepository = mock(UserStatsRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserServiceImpl(
                userRepository,
                userStatsRepository,
                roleRepository,
                passwordEncoder,
                mock(EmailService.class),
                mock(AdRepository.class),
                mock(UserAdRepository.class)
        );
    }

    @Test
    void registeringWithReferralCodeRewardsBothUsers() {
        User referrer = User.builder().id(10L).email("referrer@example.com").build();
        UserStats referrerStats = UserStats.builder().annualPoints(250).monthlyPoints(100).build();
        when(userRepository.findByReferralCode("REFERRAL123")).thenReturn(Optional.of(referrer));
        when(userRepository.existsByReferralCode(any())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(new Role());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(userStatsRepository.findWithLockByUserEmail(referrer.getEmail())).thenReturn(Optional.of(referrerStats));

        UserDto registration = new UserDto();
        registration.setEmail("new-user@example.com");
        registration.setPassword("password");
        registration.setReferralCode(" referral123 ");

        userService.saveUser(registration);

        assertEquals(1250, referrerStats.getAnnualPoints());
        assertEquals(1100, referrerStats.getMonthlyPoints());
        verify(userStatsRepository).save(referrerStats);
        org.mockito.ArgumentCaptor<UserStats> newStatsCaptor =
                org.mockito.ArgumentCaptor.forClass(UserStats.class);
        verify(userStatsRepository, org.mockito.Mockito.times(2)).save(newStatsCaptor.capture());
        UserStats newUserStats = newStatsCaptor.getAllValues().get(0);
        assertEquals(1000, newUserStats.getAnnualPoints());
        assertEquals(1000, newUserStats.getMonthlyPoints());

        org.mockito.ArgumentCaptor<User> userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User newUser = userCaptor.getValue();
        assertEquals(referrer, newUser.getReferredBy());
        assertNotNull(newUser.getReferralCode());
        assertTrue(newUser.getReferralCode().matches("[A-F0-9]{16}"));
    }

    @Test
    void getOrCreateReferralCodePersistsCodeForExistingUsers() {
        User user = User.builder().id(42L).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.existsByReferralCode(any())).thenReturn(false);

        String referralCode = userService.getOrCreateReferralCode(42L);

        assertTrue(referralCode.matches("[A-F0-9]{16}"));
        assertEquals(referralCode, user.getReferralCode());
        verify(userRepository).save(user);
    }

    @Test
    void saveUserRejectsUnknownReferralCodeWithoutSavingAccount() {
        UserDto registration = new UserDto();
        registration.setReferralCode("UNKNOWN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.saveUser(registration)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }
}
