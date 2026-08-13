package com.geekup.eventticketbookingservice.auth;

import com.geekup.eventticketbookingservice.auth.dto.AuthResponse;
import com.geekup.eventticketbookingservice.auth.dto.LoginRequest;
import com.geekup.eventticketbookingservice.auth.dto.RegisterRequest;
import com.geekup.eventticketbookingservice.security.JwtService;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import com.geekup.eventticketbookingservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john@example.com")
                .password("encodedPassword")
                .role(Role.CUSTOMER)
                .status("ACTIVE")
                .build();

        registerRequest = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        loginRequest = LoginRequest.builder()
                .email("john@example.com")
                .password("password123")
                .build();
    }

    @Test
    void register_Success() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("dummyToken");

        // Act
        AuthResponse response = authService.register(registerRequest);

        // Assert
        assertNotNull(response);
        assertEquals("dummyToken", response.getAccessToken());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_EmailAlreadyInUse_ThrowsException() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        // Arrange
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(User.class))).thenReturn("dummyToken");

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("dummyToken", response.getAccessToken());
        verify(authenticationManager, times(1)).authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );
    }
}
