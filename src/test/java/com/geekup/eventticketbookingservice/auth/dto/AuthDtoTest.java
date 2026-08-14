package com.geekup.eventticketbookingservice.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Auth DTO Unit Tests")
class AuthDtoTest {

    @Test
    @DisplayName("RegisterRequest - Test Builder, Getters, Setters, Equals, HashCode, ToString")
    void testRegisterRequest() {
        RegisterRequest req1 = RegisterRequest.builder()
                .fullName("Alice")
                .email("alice@example.com")
                .password("secret123")
                .build();

        assertEquals("Alice", req1.getFullName());
        assertEquals("alice@example.com", req1.getEmail());
        assertEquals("secret123", req1.getPassword());

        RegisterRequest req2 = new RegisterRequest();
        req2.setFullName("Alice");
        req2.setEmail("alice@example.com");
        req2.setPassword("secret123");

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertTrue(req1.toString().contains("alice@example.com"));

        RegisterRequest req3 = new RegisterRequest("Bob", "bob@example.com", "pass456");
        assertNotEquals(req1, req3);
    }

    @Test
    @DisplayName("LoginRequest - Test Builder, Getters, Setters, Equals, HashCode, ToString")
    void testLoginRequest() {
        LoginRequest req1 = LoginRequest.builder()
                .email("alice@example.com")
                .password("secret123")
                .build();

        assertEquals("alice@example.com", req1.getEmail());
        assertEquals("secret123", req1.getPassword());

        LoginRequest req2 = new LoginRequest();
        req2.setEmail("alice@example.com");
        req2.setPassword("secret123");

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertTrue(req1.toString().contains("alice@example.com"));

        LoginRequest req3 = new LoginRequest("bob@example.com", "pass456");
        assertNotEquals(req1, req3);
    }

    @Test
    @DisplayName("AuthResponse - Test Builder, Getters, Setters, Equals, HashCode, ToString")
    void testAuthResponse() {
        AuthResponse res1 = AuthResponse.builder()
                .accessToken("token123")
                .build();

        assertEquals("token123", res1.getAccessToken());

        AuthResponse res2 = new AuthResponse();
        res2.setAccessToken("token123");

        assertEquals(res1, res2);
        assertEquals(res1.hashCode(), res2.hashCode());
        assertTrue(res1.toString().contains("token123"));

        AuthResponse res3 = new AuthResponse("token456");
        assertNotEquals(res1, res3);
    }
}
