package com.soa.spiriponzi.security;

import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void loadsConfiguredAdminWithHashedPasswordAndAdminRole() {
        CustomUserDetailsService service =
                new CustomUserDetailsService(userRepository, passwordEncoder, "admin", "local-test-password");

        UserDetails admin = service.loadUserByUsername("admin");

        assertEquals("admin", admin.getUsername());
        assertTrue(passwordEncoder.matches("local-test-password", admin.getPassword()));
        assertTrue(admin.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void doesNotEnableAdminWhenPasswordIsMissing() {
        CustomUserDetailsService service =
                new CustomUserDetailsService(userRepository, passwordEncoder, "admin", "");
        when(userRepository.findByEmail("admin")).thenReturn(null);

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("admin"));
        verify(userRepository).findByEmail("admin");
    }

    @Test
    void stillLoadsRegularDatabaseUsers() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("encoded-password");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);

        CustomUserDetailsService service =
                new CustomUserDetailsService(userRepository, passwordEncoder, "admin", "");

        assertEquals("user@example.com", service.loadUserByUsername(user.getEmail()).getUsername());
    }
}
