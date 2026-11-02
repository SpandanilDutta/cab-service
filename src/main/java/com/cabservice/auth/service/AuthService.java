package com.cabservice.auth.service;

import com.cabservice.auth.dto.AuthDtos.AuthResponse;
import com.cabservice.auth.dto.AuthDtos.LoginRequest;
import com.cabservice.auth.dto.AuthDtos.RefreshTokenRequest;
import com.cabservice.common.exception.ApiException;

import com.cabservice.common.util.AppConstant;
import com.cabservice.security.jwt.JwtService;
import com.cabservice.security.jwt.RefreshTokenStore;
import com.cabservice.security.userdetails.UserPrincipal;


import com.cabservice.user.entity.User;
import com.cabservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;

    /** Login with email OR mobile + password. Wrong credentials throw AuthenticationException (handled -> 401). */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.identifier().trim(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return issueTokens(principal.getId(), principal.getUsername(), principal.getRoles());
    }

    /**
     * Refresh tokens are single-use (rotation): the old one is consumed and a new pair is issued.
     * Roles are read fresh from the database here, so a role granted meanwhile
     * (for example DRIVER after admin approval) appears in the new access token.
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        UUID userId = refreshTokenStore.consume(request.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        User user = userRepository.findById(userId)
                .filter(found -> found.getAccountStatus() == AppConstant.AccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        return issueTokens(user.getId(), user.getEmail(), user.getRoles());
    }

    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    public AuthResponse issueTokens(UUID userId, String username, Set<AppConstant.Role> roles) {
        String accessToken = jwtService.generateAccessToken(userId, username, roles);
        String refreshToken = refreshTokenStore.issue(userId);
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenTtlSeconds());
    }
}