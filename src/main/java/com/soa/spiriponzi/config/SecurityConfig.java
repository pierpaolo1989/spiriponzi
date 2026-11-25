package com.soa.spiriponzi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.web.filter.RequestContextFilter;

import com.soa.spiriponzi.controller.GoogleAuthController;
import com.soa.spiriponzi.security.GoogleOidcUserService;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    @Bean
    public static PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<GoogleOidcUserService> googleUsers) throws Exception {
        http.csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> {
                    String method = request.getMethod();
                    String path = request.getServletPath();
                    return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                            || "TRACE".equals(method))
                            && (path.equals("/withdraws") || path.equals("/claims/game") || path.startsWith("/admin/")
                                || path.equals("/auth/google/register"));
                }))
                .authorizeHttpRequests((authorize) -> authorize
                        // Pagine accessibili senza login
                        .requestMatchers("/", "/register/**", "/activate", "/resend-activation", "/index", "/faq", "/terms", "/recover-password/**", "/reset-password/**", "/menu/**",
                                "/callbacks/primeearn",
                                "/callbacks/cpx",
                                "/callbacks/offerwall",
                                "/affiliate/click/**",
                                "/auth/google/**", "/oauth2/**", "/login/oauth2/**",
                                "/sitemap.xml",
                                "/ads.txt",
                                "/img/**", "/js/**", "/css/**", "/webjars/**", "/vendor/**").permitAll()

                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/affiliate").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/**").hasRole("USER")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .failureHandler((request, response, exception) -> {
                            String status = exception instanceof DisabledException ? "unverified" : "error";
                            response.sendRedirect(request.getContextPath() + "/login?" + status);
                        })
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities()
                                    .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
                            String destination = isAdmin ? "/admin" : "/dashboard";
                            response.sendRedirect(request.getContextPath() + destination);
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .permitAll()
                );

        GoogleOidcUserService googleUserService = googleUsers.getIfAvailable();
        if (googleUserService != null) {
            http.addFilterBefore(new RequestContextFilter(), OAuth2LoginAuthenticationFilter.class);
            http.oauth2Login(oauth -> oauth.loginPage("/login")
                    .userInfoEndpoint(info -> info.oidcUserService(googleUserService))
                    .successHandler((request, response, authentication) ->
                            response.sendRedirect(request.getContextPath() + "/dashboard"))
                    .failureHandler((request, response, exception) -> {
                        if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauthException) {
                            log.warn("Google sign-in failed: {}", oauthException.getError().getErrorCode());
                        } else {
                            log.warn("Google sign-in failed: {}", exception.getClass().getSimpleName());
                        }
                        if (request.getSession(false) != null) {
                            request.getSession(false).removeAttribute(GoogleAuthController.REGISTRATION_REFERRAL);
                        }
                        boolean needsActivation = exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauthException
                                && "account_not_activated".equals(oauthException.getError().getErrorCode());
                        response.sendRedirect(request.getContextPath()
                                + (needsActivation ? "/login?unverified" : "/login?googleError"));
                    }));
        }
        return http.build();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
    }
}