package com.commerce.auth_service.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.auth_service.dto.*;
import com.commerce.auth_service.entity.RefreshToken;
import com.commerce.auth_service.entity.User;
import com.commerce.auth_service.exception.AccountNotActiveException;
import com.commerce.auth_service.exception.EmailAlreadyExistsException;
import com.commerce.auth_service.exception.InvalidCredentialsException;
import com.commerce.auth_service.repository.UserRepository;


@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RefreshTokenService   refreshTokenService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PermissionService permissionService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)  // For demo, default to ADMIN. In production, default to CUSTOMER and allow role assignment by admin.
                .status(User.Status.ACTIVE)  //or PENDING if email verification needed
                .build();

        userRepository.save(user);
        return AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .tokenType("Bearer")
                .expiresIn(0L)
                .role(user.getRole().name())
                .user(AuthResponse.UserSummary.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .status(user.getStatus().name())
                        .build())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.getStatus().isActive()) {
            throw new AccountNotActiveException(
                "Account is " + user.getStatus().name().toLowerCase() +
                ". Please verify your email or contact support."
            );
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(rawRefreshToken);
        Set<String> permissions = permissionService.getEffectivePermissions(oldToken.getUser().getId());


        User user = oldToken.getUser();

        if (!user.getStatus().isActive()) {
            refreshTokenService.revokeAllUserTokens(user.getId());
            throw new AccountNotActiveException("Account is no longer active.");
        }

        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldToken);

        String newAccessToken = jwtService.generateToken(user, permissions);

        return buildAuthResponse(user, newRefreshToken, newAccessToken);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeToken(rawRefreshToken);
    }

    public void logoutAll(String userId) {
        refreshTokenService.revokeAllUserTokens(userId);
    }


        private AuthResponse buildAuthResponse(User user) {
        Set<String> permissions = permissionService.getEffectivePermissions(user.getId());
        String accessToken   = jwtService.generateToken(user, permissions);
        RefreshToken refresh = refreshTokenService.createRefreshToken(user);
        return buildAuthResponse(user, refresh, accessToken);
    }

    private AuthResponse buildAuthResponse(User user,
                                            RefreshToken refresh,
                                            String accessToken) {
        Set<String> permissions = permissionService.getEffectivePermissions(user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refresh.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.expirationTime()/1000)
                .role(user.getRole().name())
                .permissions(permissions)
                .user(AuthResponse.UserSummary.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .status(user.getStatus().name())
                        .build())
                .build();
    }


}