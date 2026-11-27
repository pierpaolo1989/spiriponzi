package com.soa.spiriponzi.security;

import java.util.List;

import com.soa.spiriponzi.controller.GoogleAuthController;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "app.google.enabled", havingValue = "true")
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserService users;

    @Autowired
    public GoogleOidcUserService(UserService users) {
        this(users, new OidcUserService());
    }

    GoogleOidcUserService(UserService users, OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.users = users;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser googleUser = delegate.loadUser(request);
        String subject = googleUser.getIdToken().getSubject();
        String email = googleUser.getIdToken().getEmail();
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(email)
                || !Boolean.TRUE.equals(googleUser.getIdToken().getEmailVerified())) {
            throw failure("Google account must have a verified email");
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest servletRequest = attributes.getRequest();
        HttpSession session = servletRequest.getSession(false);
        Object pending = session == null ? null : session.getAttribute(GoogleAuthController.REGISTRATION_REFERRAL);
        boolean registering = pending instanceof String;
        if (session != null) {
            session.removeAttribute(GoogleAuthController.REGISTRATION_REFERRAL);
        }
        User user;
        try {
            user = users.signInWithGoogle(subject, email, registering, registering ? (String) pending : null);
        } catch (ResponseStatusException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("google_account_error"), ex.getReason(), ex);
        }
        if (!user.isEnabled()) {
            String errorCode = registering ? "google_registration_activation_required" : "account_not_activated";
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(errorCode), "Activate your account using the link sent by email");
        }
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName())).toList();
        return new LocalOidcUser(authorities, googleUser, user.getEmail());
    }

    private static OAuth2AuthenticationException failure(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("google_account_error"), message);
    }

    private static class LocalOidcUser extends DefaultOidcUser {
        private final String localEmail;

        LocalOidcUser(List<SimpleGrantedAuthority> authorities, OidcUser googleUser, String localEmail) {
            super(authorities, googleUser.getIdToken(), googleUser.getUserInfo());
            this.localEmail = localEmail;
        }

        @Override
        public String getName() {
            return localEmail;
        }
    }
}
