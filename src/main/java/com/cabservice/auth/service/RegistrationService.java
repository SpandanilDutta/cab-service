package com.cabservice.auth.service;

import com.cabservice.auth.dto.AuthDtos.AuthResponse;
import com.cabservice.auth.dto.AuthDtos.RegisterRequest;
import com.cabservice.auth.dto.AuthDtos.ResendOtpRequest;
import com.cabservice.auth.dto.AuthDtos.VerifyOtpRequest;
import com.cabservice.common.exception.ApiException;
import com.cabservice.messaging.OtpEmailMessage;
import com.cabservice.user.entity.AccountStatus;
import com.cabservice.user.entity.Role;
import com.cabservice.user.entity.User;
import com.cabservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registration flow:
 *   1. register()   -> validates, stores a PENDING registration in Redis (10 min), emails an OTP
 *   2. verifyOtp()  -> checks the OTP, THEN creates the users row + RIDER role, returns tokens
 *   3. resendOtp()  -> new OTP for a pending registration
 *
 * Nothing is written to PostgreSQL until the email is proven, so abandoned or mistyped
 * signups never block a real person's email or mobile number.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final String PENDING_KEY = "auth:reg:pending:";
    private static final String OTP_KEY = "auth:reg:otp:";
    private static final String ATTEMPTS_KEY = "auth:reg:attempts:";
    private static final String SENDS_KEY = "auth:reg:sends:";

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailPublisher otpEmailPublisher;
    private final AuthService authService;

    @Value("${cabservice.auth.registration.otp-ttl-minutes}")
    private long otpTtlMinutes;

    @Value("${cabservice.auth.registration.max-verify-attempts}")
    private int maxVerifyAttempts;

    @Value("${cabservice.auth.registration.max-sends-per-hour}")
    private int maxSendsPerHour;

    private final SecureRandom secureRandom = new SecureRandom();

    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String mobile = request.mobile().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (userRepository.existsByMobile(mobile)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this mobile number already exists");
        }

        enforceSendLimit(email); // before the expensive BCrypt hashing

        String fullName = request.fullName().trim();
        redis.opsForHash().putAll(PENDING_KEY + email, Map.of(
                "fullName", fullName,
                "mobile", mobile,
                "passwordHash", passwordEncoder.encode(request.password())));
        redis.expire(PENDING_KEY + email, Duration.ofMinutes(otpTtlMinutes));

        issueOtp(email, fullName);
    }

    public void resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.email());

        Map<Object, Object> pending = redis.opsForHash().entries(PENDING_KEY + email);
        if (pending.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No pending registration found. Please register again.");
        }

        enforceSendLimit(email);
        redis.expire(PENDING_KEY + email, Duration.ofMinutes(otpTtlMinutes));
        issueOtp(email, (String) pending.get("fullName"));
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());

        String storedOtp = redis.opsForValue().get(OTP_KEY + email);
        Map<Object, Object> pending = redis.opsForHash().entries(PENDING_KEY + email);
        if (storedOtp == null || pending.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The OTP has expired or no registration was found. Please register again.");
        }

        long attempts = incrementWithTtl(ATTEMPTS_KEY + email, Duration.ofMinutes(otpTtlMinutes));
        if (attempts > maxVerifyAttempts) {
            clearRegistration(email);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts. Please register again.");
        }

        // Constant-time comparison, so response time does not leak how many digits matched
        boolean matches = MessageDigest.isEqual(
                storedOtp.getBytes(StandardCharsets.UTF_8),
                request.otp().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Incorrect OTP. " + (maxVerifyAttempts - attempts) + " attempt(s) left.");
        }

        // Single use: delete() returns true for exactly one of several simultaneous requests
        if (!Boolean.TRUE.equals(redis.delete(OTP_KEY + email))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This OTP has already been used.");
        }

        User user = User.builder()
                .fullName((String) pending.get("fullName"))
                .mobile((String) pending.get("mobile"))
                .email(email)
                .passwordHash((String) pending.get("passwordHash"))
                .accountStatus(AccountStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(Role.RIDER))) // every registered person can ride
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two people verified the same email/mobile at nearly the same time; the DB unique constraint decided
            clearRegistration(email);
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email or mobile number already exists");
        }

        clearRegistration(email);
        return authService.issueTokens(user.getId(), user.getEmail(), user.getRoles());
    }

    // ---------------------------------------------------------------- helpers

    private void issueOtp(String email, String fullName) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        redis.opsForValue().set(OTP_KEY + email, otp, Duration.ofMinutes(otpTtlMinutes));
        redis.delete(ATTEMPTS_KEY + email); // a fresh OTP gets a fresh set of attempts
        otpEmailPublisher.publish(new OtpEmailMessage(email, fullName, otp, otpTtlMinutes));
    }

    private void enforceSendLimit(String email) {
        long sends = incrementWithTtl(SENDS_KEY + email, Duration.ofHours(1));
        if (sends > maxSendsPerHour) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many OTP requests. Please try again later.");
        }
    }

    /**
     * INCR, then make sure the key always has an expiry. If the app crashed between INCR and
     * EXPIRE on an earlier call, the key would live forever and block the user permanently;
     * the getExpire() == -1 check repairs that.
     */
    private long incrementWithTtl(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        Long remainingSeconds = redis.getExpire(key);
        if (remainingSeconds != null && remainingSeconds == -1) {
            redis.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    private void clearRegistration(String email) {
        redis.delete(List.of(PENDING_KEY + email, OTP_KEY + email, ATTEMPTS_KEY + email));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}