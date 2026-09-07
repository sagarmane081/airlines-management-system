package com.services.service;

import com.services.config.JwtUtil;
import com.services.config.TokenHasher;
import com.services.dto.LoginRequest;
import com.services.dto.LoginResponse;
import com.services.dto.SignupRequest;
import com.services.dto.UserResponse;
import com.services.entity.User;
import com.services.exception.EmailAlreadyRegisteredException;
import com.services.exception.InvalidCredentialsException;
import com.services.exception.InvalidTokenException;
import com.services.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                        StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    public UserResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyRegisteredException("Email already registered: " + request.getEmail());
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        User saved = userRepository.save(user);

        UserResponse response = new UserResponse();
        response.setId(saved.getId());
        response.setFullName(saved.getFullName());
        response.setEmail(saved.getEmail());
        response.setRole(saved.getRole());
        return response;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        return response;
    }

    /**
     * Revokes one token by writing its hash into a Redis blocklist with a TTL equal to the token's
     * own remaining lifetime - the entry self-expires exactly when the token would have anyway, so
     * no cleanup job is needed. api-gateway's JwtAuthenticationFilter checks the same key (computed
     * with the identical TokenHasher logic) on every subsequent request.
     */
    public void logout(String token) {
        Claims claims;
        try {
            claims = jwtUtil.extractAllClaims(token);
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMillis > 0) {
            redisTemplate.opsForValue().set(TokenHasher.revokedTokenKey(token), "1", Duration.ofMillis(remainingMillis));
        }
    }
}