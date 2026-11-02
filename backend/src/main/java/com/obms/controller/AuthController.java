package com.obms.controller;

import com.obms.common.ApiException;
import com.obms.dto.AuthDtos;
import com.obms.security.CurrentUser;
import com.obms.service.AuthService;
import com.obms.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, sign-in and two-factor endpoints (FR-1, FR-2).
 */
@Tag(name = "Authentication", description = "Registration, sign-in, 2FA and password reset")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CustomerService customerService;
    private final CurrentUser currentUser;

    @Operation(summary = "Register a new customer and open their first savings account")
    @PostMapping("/register")
    public ResponseEntity<AuthDtos.RegisterResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Verify password and issue a one-time code",
            description = "Returns a pre-auth token that only the OTP endpoint accepts. "
                    + "No API authority is granted until the second factor is verified.")
    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                        HttpServletRequest httpRequest) {
        return authService.login(request, clientIp(httpRequest));
    }

    @Operation(summary = "Verify the one-time code and issue an access token")
    @PostMapping("/verify-otp")
    public AuthDtos.SessionResponse verifyOtp(
            @RequestHeader(value = "X-Pre-Auth-Token", required = false) String preAuthHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AuthDtos.VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        String token = resolvePreAuthToken(preAuthHeader, authorization);
        return authService.verifyLoginOtp(token, request.code(), clientIp(httpRequest));
    }

    @Operation(summary = "Resend the sign-in code for an in-flight attempt")
    @PostMapping("/resend-otp")
    public ResponseEntity<Void> resendOtp(
            @RequestHeader(value = "X-Pre-Auth-Token", required = false) String preAuthHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.resendLoginOtp(resolvePreAuthToken(preAuthHeader, authorization));
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Start a password reset",
            description = "Always returns 202 whether or not the address is registered, "
                    + "so the endpoint cannot be used to enumerate accounts.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Complete a password reset with the emailed code")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current customer profile")
    @GetMapping("/me")
    public AuthDtos.ProfileResponse me() {
        return customerService.profile(currentUser.requireCustomerId());
    }

    @Operation(summary = "Update the editable parts of the profile")
    @PutMapping("/me")
    public AuthDtos.ProfileResponse updateProfile(
            @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        return customerService.updateProfile(currentUser.requireCustomerId(), request);
    }

    @Operation(summary = "Submit simulated KYC documents")
    @PostMapping("/kyc")
    public ResponseEntity<String> submitKyc(@Valid @RequestBody AuthDtos.KycSubmitRequest request) {
        return ResponseEntity.accepted()
                .body(customerService.submitKyc(currentUser.requireCustomerId(), request));
    }

    /**
     * Accepts the pre-auth token from either a dedicated header or the standard
     * Authorization header, so clients can use whichever is convenient.
     */
    private String resolvePreAuthToken(String preAuthHeader, String authorization) {
        if (preAuthHeader != null && !preAuthHeader.isBlank()) {
            return preAuthHeader.trim();
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        throw ApiException.unauthorized("Provide the pre-auth token from the sign-in step.");
    }

    /**
     * @return the originating address, preferring the proxy header when present
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
