package com.geekup.eventticketbookingservice.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Entity Unit Tests")
class UserTest {

    @Nested
    @DisplayName("UserDetails Implementation Tests")
    class UserDetailsTests {

        @Test
        @DisplayName("getAuthorities() should return ROLE_CUSTOMER when role is CUSTOMER")
        void getAuthorities_CustomerRole() {
            User user = User.builder().role(Role.CUSTOMER).build();

            Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

            assertNotNull(authorities);
            assertEquals(1, authorities.size());
            assertEquals("ROLE_CUSTOMER", authorities.iterator().next().getAuthority());
        }

        @Test
        @DisplayName("getAuthorities() should return ROLE_ADMIN when role is ADMIN")
        void getAuthorities_AdminRole() {
            User user = User.builder().role(Role.ADMIN).build();

            Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

            assertNotNull(authorities);
            assertEquals(1, authorities.size());
            assertEquals("ROLE_ADMIN", authorities.iterator().next().getAuthority());
        }

        @Test
        @DisplayName("getUsername() should return email")
        void getUsername_ReturnsEmail() {
            User user = User.builder().email("test@example.com").build();
            assertEquals("test@example.com", user.getUsername());
        }

        @Test
        @DisplayName("isAccountNonExpired() should always return true")
        void isAccountNonExpired_ReturnsTrue() {
            User user = new User();
            assertTrue(user.isAccountNonExpired());
        }

        @Test
        @DisplayName("isCredentialsNonExpired() should always return true")
        void isCredentialsNonExpired_ReturnsTrue() {
            User user = new User();
            assertTrue(user.isCredentialsNonExpired());
        }

        @ParameterizedTest(name = "isAccountNonLocked() with status ''{0}'' should return false for SUSPENDED and true otherwise")
        @ValueSource(strings = {"ACTIVE", "INACTIVE", "PENDING", "DELETED"})
        @NullSource
        void isAccountNonLocked_WhenNotSuspended_ReturnsTrue(String status) {
            User user = User.builder().status(status).build();
            assertTrue(user.isAccountNonLocked());
        }

        @Test
        @DisplayName("isAccountNonLocked() should return false when status is SUSPENDED")
        void isAccountNonLocked_WhenSuspended_ReturnsFalse() {
            User user = User.builder().status("SUSPENDED").build();
            assertFalse(user.isAccountNonLocked());
        }

        @Test
        @DisplayName("isEnabled() should return true only when status is ACTIVE")
        void isEnabled_WhenActive_ReturnsTrue() {
            User user = User.builder().status("ACTIVE").build();
            assertTrue(user.isEnabled());
        }

        @ParameterizedTest(name = "isEnabled() with status ''{0}'' should return false")
        @ValueSource(strings = {"SUSPENDED", "INACTIVE", "PENDING", "DISABLED"})
        @NullSource
        void isEnabled_WhenNotActive_ReturnsFalse(String status) {
            User user = User.builder().status(status).build();
            assertFalse(user.isEnabled());
        }
    }

    @Nested
    @DisplayName("User Properties & Builder Tests")
    class PropertiesAndBuilderTests {

        @Test
        @DisplayName("Should correctly create User using Builder and getters")
        void testUserBuilderAndGetters() {
            LocalDateTime now = LocalDateTime.now();

            User user = User.builder()
                    .id(100L)
                    .email("user@example.com")
                    .password("hashed_password")
                    .fullName("Jane Doe")
                    .role(Role.CUSTOMER)
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertEquals(100L, user.getId());
            assertEquals("user@example.com", user.getEmail());
            assertEquals("hashed_password", user.getPassword());
            assertEquals("Jane Doe", user.getFullName());
            assertEquals(Role.CUSTOMER, user.getRole());
            assertEquals("ACTIVE", user.getStatus());
            assertEquals(now, user.getCreatedAt());
            assertEquals(now, user.getUpdatedAt());
        }

        @Test
        @DisplayName("Should correctly set and get fields using setters")
        void testUserSetters() {
            LocalDateTime now = LocalDateTime.now();
            User user = new User();

            user.setId(200L);
            user.setEmail("admin@example.com");
            user.setPassword("admin_hashed_pw");
            user.setFullName("Admin User");
            user.setRole(Role.ADMIN);
            user.setStatus("ACTIVE");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);

            assertEquals(200L, user.getId());
            assertEquals("admin@example.com", user.getEmail());
            assertEquals("admin_hashed_pw", user.getPassword());
            assertEquals("Admin User", user.getFullName());
            assertEquals(Role.ADMIN, user.getRole());
            assertEquals("ACTIVE", user.getStatus());
            assertEquals(now, user.getCreatedAt());
            assertEquals(now, user.getUpdatedAt());
        }

        @Test
        @DisplayName("Should support all-args constructor")
        void testAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            User user = new User(
                    1L,
                    "allargs@example.com",
                    "secret",
                    "Full Name",
                    Role.CUSTOMER,
                    "ACTIVE",
                    now,
                    now
            );

            assertEquals(1L, user.getId());
            assertEquals("allargs@example.com", user.getEmail());
            assertEquals("secret", user.getPassword());
            assertEquals("Full Name", user.getFullName());
            assertEquals(Role.CUSTOMER, user.getRole());
            assertEquals("ACTIVE", user.getStatus());
            assertEquals(now, user.getCreatedAt());
            assertEquals(now, user.getUpdatedAt());
        }
    }
}
