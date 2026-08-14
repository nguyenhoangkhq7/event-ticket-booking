package com.geekup.eventticketbookingservice.security;

import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    // 256-bit secret key for HMAC-SHA256
    private static final String SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 86400000L; // 24 hours

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION_MS);

        testUser = User.builder()
                .id(42L)
                .email("alex@example.com")
                .fullName("Alex Doe")
                .password("encoded_pass")
                .role(Role.CUSTOMER)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("generateToken generates valid non-empty JWT for user")
    void generateToken_Success() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(jwtService.isTokenValid(token));
        assertEquals("alex@example.com", jwtService.extractEmail(token));
    }

    @Test
    @DisplayName("generateToken and extractEmail for ADMIN user")
    void generateToken_AdminUser() {
        User admin = User.builder()
                .id(1L)
                .email("admin@eventticket.com")
                .role(Role.ADMIN)
                .build();

        String token = jwtService.generateToken(admin);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals("admin@eventticket.com", jwtService.extractEmail(token));
    }

    @Test
    @DisplayName("isTokenValid returns false for expired token")
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        // Set expiration to negative value (-1 hour)
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -3600000L);

        String expiredToken = jwtService.generateToken(testUser);

        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    @DisplayName("isTokenValid returns false for tampered token")
    void isTokenValid_TamperedToken_ReturnsFalse() {
        String token = jwtService.generateToken(testUser);
        String tamperedToken = token + "corrupted";

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }

    @Test
    @DisplayName("isTokenValid returns false for completely invalid/malformed string")
    void isTokenValid_MalformedString_ReturnsFalse() {
        assertFalse(jwtService.isTokenValid("not.a.valid.jwt.token"));
        assertFalse(jwtService.isTokenValid("randomString"));
        assertFalse(jwtService.isTokenValid(""));
    }

    @Test
    @DisplayName("isTokenValid returns false for token signed with different secret key")
    void isTokenValid_DifferentSecretKey_ReturnsFalse() {
        String token = jwtService.generateToken(testUser);

        // Switch secret key on jwtService to simulate token from different server
        ReflectionTestUtils.setField(jwtService, "secretKey", "999E635266556A586E3272357538782F413F4428472B4B6250645367566B5999");

        assertFalse(jwtService.isTokenValid(token));
    }
}
