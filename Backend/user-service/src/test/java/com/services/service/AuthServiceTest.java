package com.services.service;

import com.services.config.JwtUtil;
import com.services.dto.LoginRequest;
import com.services.dto.LoginResponse;
import com.services.dto.SignupRequest;
import com.services.dto.UserResponse;
import com.services.entity.Role;
import com.services.entity.User;
import com.services.exception.EmailAlreadyRegisteredException;
import com.services.exception.InvalidCredentialsException;
import com.services.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JwtUtil is mocked here rather than used for real, so this test never touches JwtConstant's
 * env-var read (JWT_SECRET) - keeps the test hermetic and independent of how it's run.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupSavesEncodedPasswordAndReturnsUser() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        SignupRequest request = new SignupRequest("Jane Doe", "new@example.com", "secret123", Role.ROLE_CUSTOMER);
        UserResponse result = authService.signup(request);

        assertEquals(1L, result.getId());
        assertEquals("new@example.com", result.getEmail());
        verify(userRepository).save(argThat(u -> "encoded-hash".equals(u.getPassword())));
    }

    @Test
    void signupThrowsWhenEmailAlreadyRegistered() {
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(new User()));

        SignupRequest request = new SignupRequest("Jane Doe", "taken@example.com", "secret123", Role.ROLE_CUSTOMER);

        assertThrows(EmailAlreadyRegisteredException.class, () -> authService.signup(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenOnCorrectCredentials() {
        User user = new User(1L, "Jane Doe", "jane@example.com", "encoded-hash", Role.ROLE_CUSTOMER);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "jane@example.com", "ROLE_CUSTOMER")).thenReturn("fake-jwt");

        LoginResponse result = authService.login(new LoginRequest("jane@example.com", "secret123"));

        assertEquals("fake-jwt", result.getToken());
        assertEquals(Role.ROLE_CUSTOMER, result.getRole());
    }

    @Test
    void loginThrowsWhenEmailNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("ghost@example.com", "whatever")));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        User user = new User(1L, "Jane Doe", "jane@example.com", "encoded-hash", Role.ROLE_CUSTOMER);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("jane@example.com", "wrong")));
        verifyNoInteractions(jwtUtil);
    }
}
