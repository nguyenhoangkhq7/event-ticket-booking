package com.geekup.eventticketbookingservice.auth;

import com.geekup.eventticketbookingservice.auth.dto.AuthResponse;
import com.geekup.eventticketbookingservice.auth.dto.LoginRequest;
import com.geekup.eventticketbookingservice.auth.dto.RegisterRequest;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("password123")
                .build();

        loginRequest = LoginRequest.builder()
                .email("john@example.com")
                .password("password123")
                .build();

        authResponse = AuthResponse.builder()
                .accessToken("mock-jwt-token")
                .build();
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Should successfully register user and return ApiResponse<AuthResponse> with HTTP 200")
        void register_Success() {
            // Arrange
            when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

            // Act
            ResponseEntity<ApiResponse<AuthResponse>> response = authController.register(registerRequest);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertNotNull(response.getBody().getData());
            assertEquals("mock-jwt-token", response.getBody().getData().getAccessToken());
            verify(authService, times(1)).register(registerRequest);
        }

        @Test
        @DisplayName("Should propagate exception when authService throws exception on register")
        void register_ServiceThrowsException_PropagatesException() {
            // Arrange
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new AppException(ErrorCode.EMAIL_ALREADY_EXISTS));

            // Act & Assert
            AppException exception = assertThrows(
                    AppException.class,
                    () -> authController.register(registerRequest)
            );
            assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
            verify(authService, times(1)).register(registerRequest);
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should successfully login user and return ApiResponse<AuthResponse> with HTTP 200")
        void login_Success() {
            // Arrange
            when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

            // Act
            ResponseEntity<ApiResponse<AuthResponse>> response = authController.login(loginRequest);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertNotNull(response.getBody().getData());
            assertEquals("mock-jwt-token", response.getBody().getData().getAccessToken());
            verify(authService, times(1)).login(loginRequest);
        }

        @Test
        @DisplayName("Should propagate exception when authService throws exception on login")
        void login_ServiceThrowsException_PropagatesException() {
            // Arrange
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new RuntimeException("Bad credentials"));

            // Act & Assert
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> authController.login(loginRequest)
            );
            assertEquals("Bad credentials", exception.getMessage());
            verify(authService, times(1)).login(loginRequest);
        }
    }
}
