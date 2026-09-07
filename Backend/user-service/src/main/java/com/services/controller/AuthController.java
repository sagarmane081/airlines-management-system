package com.services.controller;

import com.services.dto.LoginRequest;
import com.services.dto.LoginResponse;
import com.services.dto.SignupRequest;
import com.services.dto.UserResponse;
import com.services.exception.InvalidTokenException;
import com.services.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@RequestBody SignupRequest request) {
        return new ResponseEntity<>(authService.signup(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Reads the raw Authorization header directly rather than a gateway-forwarded X-User-Id -
    // /auth/** bypasses JwtAuthenticationFilter entirely (same as signup/login), so this endpoint
    // parses and validates the token itself via AuthService. A missing header is rejected by
    // @RequestHeader itself (400, before this method runs) - the check here only needs to catch a
    // header that's present but doesn't carry a Bearer token.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (!authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization header must be a Bearer token");
        }
        authService.logout(authHeader.substring(7));
        return ResponseEntity.noContent().build();
    }
}