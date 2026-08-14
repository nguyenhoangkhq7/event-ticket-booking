package com.geekup.eventticketbookingservice.common.ratelimit;

import com.geekup.eventticketbookingservice.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(jwtService);
    }

    @Nested
    @DisplayName("Booking Rate Limit Tests")
    class BookingRateLimitTests {

        @Test
        @DisplayName("allows up to 5 bookings per minute per user, blocks 6th")
        void doFilter_allowsBookingWithinLimit_blocksWhenExceeded() throws ServletException, IOException {
            String token = "valid-token";
            when(jwtService.extractEmail(token)).thenReturn("user@example.com");

            // 5 requests allowed
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
                request.addHeader("Authorization", "Bearer " + token);
                MockHttpServletResponse response = new MockHttpServletResponse();

                rateLimitFilter.doFilter(request, response, filterChain);
                assertEquals(200, response.getStatus());
            }

            // 6th request blocked
            MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/bookings");
            blockedRequest.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

            rateLimitFilter.doFilter(blockedRequest, blockedResponse, filterChain);

            assertEquals(429, blockedResponse.getStatus());
            assertEquals("60", blockedResponse.getHeader("Retry-After"));
            assertTrue(blockedResponse.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
            verify(filterChain, times(5)).doFilter(any(), any());
        }

        @Test
        @DisplayName("booking request without Authorization header passes through to filter chain")
        void doFilter_BookingWithoutAuthHeader_PassesThrough() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitFilter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            verify(filterChain, times(1)).doFilter(request, response);
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("booking request with invalid auth header causing extractEmail exception passes through")
        void doFilter_BookingWithFaultyAuthHeader_PassesThrough() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
            request.addHeader("Authorization", "Bearer invalid-jwt");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.extractEmail("invalid-jwt")).thenThrow(new RuntimeException("malformed token"));

            rateLimitFilter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            verify(filterChain, times(1)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Login Rate Limit Tests")
    class LoginRateLimitTests {

        @Test
        @DisplayName("allows up to 10 login requests per minute per IP, blocks 11th")
        void doFilter_allowsLoginWithinLimit_blocksWhenExceeded() throws ServletException, IOException {
            String clientIp = "192.168.1.100";

            // 10 requests allowed
            for (int i = 0; i < 10; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
                request.setRemoteAddr(clientIp);
                MockHttpServletResponse response = new MockHttpServletResponse();

                rateLimitFilter.doFilter(request, response, filterChain);
                assertEquals(200, response.getStatus());
            }

            // 11th request blocked
            MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/auth/login");
            blockedRequest.setRemoteAddr(clientIp);
            MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

            rateLimitFilter.doFilter(blockedRequest, blockedResponse, filterChain);

            assertEquals(429, blockedResponse.getStatus());
            assertEquals("60", blockedResponse.getHeader("Retry-After"));
            assertTrue(blockedResponse.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
            verify(filterChain, times(10)).doFilter(any(), any());
        }

        @Test
        @DisplayName("login rate limiting extracts client IP from X-Forwarded-For comma-separated header")
        void doFilter_LoginWithXForwardedFor_ExtractsFirstIp() throws ServletException, IOException {
            String forwardedHeader = "203.0.113.195, 70.41.3.18, 150.172.238.178";

            for (int i = 0; i < 10; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
                request.addHeader("X-Forwarded-For", forwardedHeader);
                MockHttpServletResponse response = new MockHttpServletResponse();

                rateLimitFilter.doFilter(request, response, filterChain);
                assertEquals(200, response.getStatus());
            }

            // 11th request from same forwarded IP
            MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/auth/login");
            blockedRequest.addHeader("X-Forwarded-For", forwardedHeader);
            MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

            rateLimitFilter.doFilter(blockedRequest, blockedResponse, filterChain);
            assertEquals(429, blockedResponse.getStatus());

            // A different IP is still allowed
            MockHttpServletRequest otherRequest = new MockHttpServletRequest("POST", "/api/auth/login");
            otherRequest.addHeader("X-Forwarded-For", "198.51.100.1");
            MockHttpServletResponse otherResponse = new MockHttpServletResponse();

            rateLimitFilter.doFilter(otherRequest, otherResponse, filterChain);
            assertEquals(200, otherResponse.getStatus());
        }
    }

    @Nested
    @DisplayName("Non-rate-limited Paths")
    class NonRateLimitedPathTests {

        @Test
        @DisplayName("GET request to /api/concerts is not rate-limited")
        void doFilter_GetConcerts_PassesThrough() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/concerts");
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitFilter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            verify(filterChain, times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("GET request to /api/bookings is not rate-limited")
        void doFilter_GetBookings_PassesThrough() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings");
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitFilter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus());
            verify(filterChain, times(1)).doFilter(request, response);
        }
    }
}
