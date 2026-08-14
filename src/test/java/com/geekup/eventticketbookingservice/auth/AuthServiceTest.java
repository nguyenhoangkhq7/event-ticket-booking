package com.geekup.eventticketbookingservice.auth;

import com.geekup.eventticketbookingservice.auth.dto.AuthResponse;
import com.geekup.eventticketbookingservice.auth.dto.LoginRequest;
import com.geekup.eventticketbookingservice.auth.dto.RegisterRequest;
import com.geekup.eventticketbookingservice.security.JwtService;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import com.geekup.eventticketbookingservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
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

    @Nested
    @DisplayName("register() Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should successfully register a new user with CUSTOMER role, ACTIVE status, and hashed password")
        void register_Success() {
            // Arrange
            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
            when(jwtService.generateToken(any(User.class))).thenReturn("dummyToken");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            // Act
            AuthResponse response = authService.register(registerRequest);

            // Assert
            assertNotNull(response);
            assertEquals("dummyToken", response.getAccessToken());

            verify(userRepository, times(1)).save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();
            assertEquals("John Doe", capturedUser.getFullName());
            assertEquals("john@example.com", capturedUser.getEmail());
            assertEquals("encodedPassword", capturedUser.getPassword());
            assertEquals(Role.CUSTOMER, capturedUser.getRole());
            assertEquals("ACTIVE", capturedUser.getStatus());

            verify(jwtService, times(1)).generateToken(capturedUser);
        }

        @Test
        @DisplayName("Should throw AppException with EMAIL_ALREADY_EXISTS when email is already in use")
        void register_EmailAlreadyInUse_ThrowsException() {
            // Arrange
            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

            // Act & Assert
            com.geekup.eventticketbookingservice.common.exception.AppException exception = assertThrows(
                    com.geekup.eventticketbookingservice.common.exception.AppException.class,
                    () -> authService.register(registerRequest)
            );
            assertEquals(com.geekup.eventticketbookingservice.common.exception.ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());

            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(anyString());
            verify(jwtService, never()).generateToken(any(User.class));
        }
    }

    @Nested
    @DisplayName("login() Tests")
    class LoginTests {

        @Test
        @DisplayName("Should successfully authenticate and return AuthResponse with JWT token")
        void login_Success() {
            // Arrange
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(testUser)).thenReturn("dummyToken");

            // Act
            AuthResponse response = authService.login(loginRequest);

            // Assert
            assertNotNull(response);
            assertEquals("dummyToken", response.getAccessToken());

            verify(authenticationManager, times(1)).authenticate(
                    new UsernamePasswordAuthenticationToken("john@example.com", "password123")
            );
            verify(userRepository, times(1)).findByEmail("john@example.com");
            verify(jwtService, times(1)).generateToken(testUser);
        }

        @Test
        @DisplayName("Should propagate BadCredentialsException when authentication fails")
        void login_InvalidCredentials_ThrowsAuthenticationException() {
            // Arrange
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Invalid email or password"));

            // Act & Assert
            assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

            verify(userRepository, never()).findByEmail(anyString());
            verify(jwtService, never()).generateToken(any(User.class));
        }

        @Test
        @DisplayName("Should throw NoSuchElementException when user is authenticated but not found in repository")
        void login_UserNotFoundInRepo_ThrowsNoSuchElementException() {
            // Arrange
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NoSuchElementException.class, () -> authService.login(loginRequest));

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(jwtService, never()).generateToken(any(User.class));
        }
    }
}
