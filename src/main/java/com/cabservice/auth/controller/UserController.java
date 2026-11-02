package com.cabservice.user.controller;

import com.cabservice.auth.dto.AuthDtos.CurrentUserResponse;
import com.cabservice.security.config.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Smoke-test endpoint: proves the JWT filter works. It answers from the token alone,
 * with no database query. A full profile endpoint comes with the profile module.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal,
                                  Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .sorted()
                .toList();
        return new CurrentUserResponse(principal.userId(), principal.username(), roles);
    }
}