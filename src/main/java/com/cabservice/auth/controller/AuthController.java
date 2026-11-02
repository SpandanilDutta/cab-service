package com.cabservice.auth.controller;

import com.cabservice.auth.dto.AuthDtos.AuthResponse;
import com.cabservice.auth.dto.AuthDtos.LoginRequest;
import com.cabservice.auth.dto.AuthDtos.MessageResponse;
import com.cabservice.auth.dto.AuthDtos.RefreshTokenRequest;
import com.cabservice.auth.dto.AuthDtos.RegisterRequest;
import com.cabservice.auth.dto.AuthDtos.ResendOtpRequest;
import com.cabservice.auth.dto.AuthDtos.VerifyOtpRequest;
import com.cabservice.auth.service.AuthService;
import com.cabservice.auth.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller: validate the request, call a service, shape the response. No business logic here. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
        return ResponseEntity.accepted().body(new MessageResponse("An OTP has been sent to your email."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        registrationService.resendOtp(request);
        return ResponseEntity.accepted().body(new MessageResponse("A new OTP has been sent to your email."));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return new MessageResponse("Logged out");
    }
}