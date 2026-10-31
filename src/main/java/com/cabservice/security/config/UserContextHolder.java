package com.cabservice.security.config;


import com.cabservice.user.entity.User;

import com.cabservice.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class UserContextHolder {

    public static UserRepository userRepository;
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    // Inject UserRepository statically
    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        UserContextHolder.userRepository = userRepository;
    }

    /**
     * Get the current user ID from JWT/Security context automatically
     */
    public static @Nullable UUID getCurrentUserId() {
        // First try to get from ThreadLocal (if manually set)
        UUID userId = currentUserId.get();
        if (userId != null) {
            return userId;
        }

        // Auto-extract from JWT/Security Context
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                    !authentication.getName().equals("anonymousUser")) {

                String username;
                Object principal = authentication.getPrincipal();

                // Handle different principal types
                switch (principal) {
                    case User user1 -> {
                        // If you have custom UserDetails with direct user ID access
                        return user1.getId();
                        // If you have custom UserDetails with direct user ID access
                    }
                    case org.springframework.security.core.userdetails.UserDetails userDetails ->
                        // Standard Spring Security UserDetails
                            username = userDetails.getUsername();
                    case String s ->
                        // JWT often stores username as string principal
                            username = s;
                    case null, default -> username = null;
                }

                // If we have a username, lookup user ID from a database
                if (username != null && userRepository != null) {
                    User user = userRepository.findByUsername(username)
                            .or(() -> userRepository.findByEmail(username))
                            .or(() -> userRepository.findByMobile(username))
                            .orElse(null);

                    if (user != null) {
                        UUID fetchedUserId = user.getId();
                        log.debug("Auto-resolved user ID {} for username: {}", fetchedUserId, username);
                        return fetchedUserId;
                    } else {
                        log.warn("User not found for username: {}", username);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error getting user from security context", e);
        }

        return null;
    }

    /**
     * Manual override - only use if needed
     */
    public static void setCurrentUserId(UUID userId) {
        currentUserId.set(userId);
        log.debug("Manually set current user ID: {}", userId);
    }

    /**
     * Clear manual override
     */
    public static void clear() {
        currentUserId.remove();
        log.debug("Cleared manually set user ID");
    }

    /**
     * Get the current username from security context
     */
    public static @Nullable String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Error getting username from security context", e);
        }
        return null;
    }

    /**
     * Execute a block of code with a specific user ID (rarely needed now)
     */
    public static <T> T executeAsUser(UUID userId, java.util.function.@NotNull Supplier<T> action) {
        UUID previousUserId = currentUserId.get();
        try {
            setCurrentUserId(userId);
            return action.get();
        } finally {
            if (previousUserId != null) {
                setCurrentUserId(previousUserId);
            } else {
                clear();
            }
        }
    }
}


