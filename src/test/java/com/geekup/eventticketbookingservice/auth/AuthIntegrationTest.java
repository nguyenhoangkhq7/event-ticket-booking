package com.geekup.eventticketbookingservice.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.auth.dto.LoginRequest;
import com.geekup.eventticketbookingservice.auth.dto.RegisterRequest;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Auth Module Integration Tests")
public class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Register Endpoint Tests (POST /api/auth/register)")
    class RegisterIntegrationTests {

        @Test
        @DisplayName("Register new user successfully returns 200 OK and accessToken wrapped in ApiResponse")
        void register_Success() throws Exception {
            String email = "reg_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            RegisterRequest request = RegisterRequest.builder()
                    .fullName("New Test Customer")
                    .email(email)
                    .password("securePassword123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isString())
                    .andExpect(jsonPath("$.data.accessToken", not(emptyString())));
        }

        @Test
        @DisplayName("Register with duplicate email returns 409 Conflict with EMAIL_ALREADY_EXISTS")
        void register_DuplicateEmail_Returns409Conflict() throws Exception {
            String email = "dup_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            RegisterRequest request = RegisterRequest.builder()
                    .fullName("First User")
                    .email(email)
                    .password("securePassword123")
                    .build();

            // First registration - OK
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Second registration with same email - Conflict
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.EMAIL_ALREADY_EXISTS.getCode()));
        }

        @Test
        @DisplayName("Register with invalid email format returns 400 Bad Request (VALIDATION_ERROR)")
        void register_InvalidEmail_Returns400BadRequest() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .fullName("Invalid Email User")
                    .email("not-a-valid-email")
                    .password("securePassword123")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Register with short password (< 6 chars) returns 400 Bad Request (VALIDATION_ERROR)")
        void register_ShortPassword_Returns400BadRequest() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .fullName("Short Pass User")
                    .email("shortpass@example.com")
                    .password("12345")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("Login Endpoint Tests (POST /api/auth/login)")
    class LoginIntegrationTests {

        @Test
        @DisplayName("Login with valid credentials returns 200 OK and accessToken wrapped in ApiResponse")
        void login_Success() throws Exception {
            // Use seeded user customer1@example.com / password123
            LoginRequest request = LoginRequest.builder()
                    .email("customer1@example.com")
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isString())
                    .andExpect(jsonPath("$.data.accessToken", not(emptyString())));
        }

        @Test
        @DisplayName("Login with wrong password returns 401 Unauthorized")
        void login_WrongPassword_Returns401Unauthorized() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("customer1@example.com")
                    .password("wrongPassword999")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Login with blank email returns 400 Bad Request (VALIDATION_ERROR)")
        void login_BlankEmail_Returns400BadRequest() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("")
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }
}
