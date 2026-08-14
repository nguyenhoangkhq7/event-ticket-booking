package com.geekup.eventticketbookingservice.inventory;

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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRedisService Unit Tests")
class InventoryRedisServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @InjectMocks
    private InventoryRedisService inventoryRedisService;

    private static final Long CATEGORY_ID = 100L;
    private static final String REDIS_KEY = "inventory:100";

    @Nested
    @DisplayName("preWarm tests")
    class PreWarmTests {

        @Test
        @DisplayName("preWarm successfully sets initial inventory in Redis")
        void preWarm_Success() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            inventoryRedisService.preWarm(CATEGORY_ID, 50);

            verify(redisTemplate.opsForValue(), times(1)).set(REDIS_KEY, "50");
        }

        @Test
        @DisplayName("preWarm handles Redis exception gracefully without throwing")
        void preWarm_RedisException_HandledGracefully() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(valueOperations).set(anyString(), anyString());

            assertDoesNotThrow(() -> inventoryRedisService.preWarm(CATEGORY_ID, 50));
            verify(valueOperations, times(1)).set(REDIS_KEY, "50");
        }
    }

    @Nested
    @DisplayName("tryDecrement tests")
    class TryDecrementTests {

        @Test
        @DisplayName("tryDecrement returns true when remaining inventory is positive")
        void tryDecrement_PositiveRemaining_ReturnsTrue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.decrement(REDIS_KEY, 2)).thenReturn(3L);

            boolean result = inventoryRedisService.tryDecrement(CATEGORY_ID, 2);

            assertTrue(result);
            verify(valueOperations, times(1)).decrement(REDIS_KEY, 2);
            verify(valueOperations, never()).increment(anyString(), anyLong());
        }

        @Test
        @DisplayName("tryDecrement returns true when remaining inventory is exactly zero")
        void tryDecrement_ZeroRemaining_ReturnsTrue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.decrement(REDIS_KEY, 5)).thenReturn(0L);

            boolean result = inventoryRedisService.tryDecrement(CATEGORY_ID, 5);

            assertTrue(result);
            verify(valueOperations, times(1)).decrement(REDIS_KEY, 5);
            verify(valueOperations, never()).increment(anyString(), anyLong());
        }

        @Test
        @DisplayName("tryDecrement throws SERVICE_UNAVAILABLE when key does not exist in Redis (fail-fast to protect DB)")
        void tryDecrement_KeyNotFound_ThrowsServiceUnavailable() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.decrement(REDIS_KEY, 2)).thenReturn(null);

            AppException ex = assertThrows(AppException.class,
                    () -> inventoryRedisService.tryDecrement(CATEGORY_ID, 2));

            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
            verify(valueOperations, times(1)).decrement(REDIS_KEY, 2);
            verify(valueOperations, never()).increment(anyString(), anyLong());
        }

        @Test
        @DisplayName("tryDecrement rolls back decrement and returns false when inventory is sold out (remaining < 0)")
        void tryDecrement_SoldOut_RollsBackAndReturnsFalse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.decrement(REDIS_KEY, 3)).thenReturn(-1L);

            boolean result = inventoryRedisService.tryDecrement(CATEGORY_ID, 3);

            assertFalse(result);
            verify(valueOperations, times(1)).decrement(REDIS_KEY, 3);
            verify(valueOperations, times(1)).increment(REDIS_KEY, 3);
        }

        @Test
        @DisplayName("tryDecrement throws SERVICE_UNAVAILABLE (circuit breaker) when Redis throws an exception")
        void tryDecrement_RedisException_ThrowsServiceUnavailable() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.decrement(REDIS_KEY, 1)).thenThrow(new RuntimeException("Redis timeout"));

            AppException ex = assertThrows(AppException.class,
                    () -> inventoryRedisService.tryDecrement(CATEGORY_ID, 1));

            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
            verify(valueOperations, times(1)).decrement(REDIS_KEY, 1);
        }
    }

    @Nested
    @DisplayName("release tests")
    class ReleaseTests {

        @Test
        @DisplayName("release increments Redis inventory counter by quantity")
        void release_Success() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(REDIS_KEY, 3)).thenReturn(13L);

            inventoryRedisService.release(CATEGORY_ID, 3);

            verify(valueOperations, times(1)).increment(REDIS_KEY, 3);
        }

        @Test
        @DisplayName("release handles Redis exception gracefully without throwing")
        void release_RedisException_HandledGracefully() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(REDIS_KEY, 2)).thenThrow(new RuntimeException("Redis unavailable"));

            assertDoesNotThrow(() -> inventoryRedisService.release(CATEGORY_ID, 2));
            verify(valueOperations, times(1)).increment(REDIS_KEY, 2);
        }
    }

    @Nested
    @DisplayName("getAvailable tests")
    class GetAvailableTests {

        @Test
        @DisplayName("getAvailable returns parsed integer when key exists with positive value")
        void getAvailable_KeyExists_ReturnsQuantity() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenReturn("42");

            Integer result = inventoryRedisService.getAvailable(CATEGORY_ID);

            assertNotNull(result);
            assertEquals(42, result);
        }

        @Test
        @DisplayName("getAvailable returns zero when stored value is negative")
        void getAvailable_NegativeValue_ReturnsZero() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenReturn("-5");

            Integer result = inventoryRedisService.getAvailable(CATEGORY_ID);

            assertNotNull(result);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("getAvailable returns null when key does not exist")
        void getAvailable_KeyNotFound_ReturnsNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenReturn(null);

            Integer result = inventoryRedisService.getAvailable(CATEGORY_ID);

            assertNull(result);
        }

        @Test
        @DisplayName("getAvailable returns null when Redis throws exception")
        void getAvailable_RedisException_ReturnsNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(REDIS_KEY)).thenThrow(new RuntimeException("Connection error"));

            Integer result = inventoryRedisService.getAvailable(CATEGORY_ID);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("isAvailable tests")
    class IsAvailableTests {

        @Test
        @DisplayName("isAvailable returns true when connection factory exists and hasKey returns true")
        void isAvailable_Healthy_ReturnsTrue() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.hasKey("health_check_dummy_nonexistent")).thenReturn(Boolean.TRUE);

            boolean healthy = inventoryRedisService.isAvailable();

            assertTrue(healthy);
        }

        @Test
        @DisplayName("isAvailable returns false when connection factory is null")
        void isAvailable_ConnectionFactoryNull_ReturnsFalse() {
            when(redisTemplate.getConnectionFactory()).thenReturn(null);

            boolean healthy = inventoryRedisService.isAvailable();

            assertFalse(healthy);
        }

        @Test
        @DisplayName("isAvailable returns false when hasKey returns false")
        void isAvailable_HasKeyFalse_ReturnsFalse() {
            when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
            when(redisTemplate.hasKey("health_check_dummy_nonexistent")).thenReturn(Boolean.FALSE);

            boolean healthy = inventoryRedisService.isAvailable();

            assertFalse(healthy);
        }

        @Test
        @DisplayName("isAvailable returns false when exception occurs")
        void isAvailable_RedisException_ReturnsFalse() {
            when(redisTemplate.getConnectionFactory()).thenThrow(new RuntimeException("Redis down"));

            boolean healthy = inventoryRedisService.isAvailable();

            assertFalse(healthy);
        }
    }
}
