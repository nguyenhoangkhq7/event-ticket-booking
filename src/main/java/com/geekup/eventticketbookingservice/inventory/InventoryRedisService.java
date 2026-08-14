package com.geekup.eventticketbookingservice.inventory;

import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "inventory:";

    /**
     * Pre-warm inventory in Redis with available quantity.
     */
    public void preWarm(Long categoryId, int availableQuantity) {
        String key = KEY_PREFIX + categoryId;
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(availableQuantity));
            log.info("Pre-warmed inventory for category {}: {} tickets", categoryId, availableQuantity);
        } catch (Exception e) {
            log.warn("Failed to pre-warm Redis inventory for category {}: {}", categoryId, e.getMessage());
        }
    }

    /**
     * Try decrementing inventory atomically in Redis (First-line defense / Pre-filter).
     * Applies Fail-Fast / Circuit Breaker protection: If Redis is unavailable or key is missing,
     * throws SERVICE_UNAVAILABLE (503) instead of falling back to DB, preventing connection pool
     * exhaustion and cascading failure during flash sales.
     *
     * @return true if inventory deducted successfully
     *         false if sold out in Redis
     * @throws AppException with SERVICE_UNAVAILABLE if Redis fails or key is missing
     */
    public boolean tryDecrement(Long categoryId, int quantity) {
        String key = KEY_PREFIX + categoryId;
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key, quantity);

            if (remaining == null) {
                log.error("Inventory key not found in Redis for category {}. Tripping circuit breaker to protect DB from unmitigated load.", categoryId);
                throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Inventory cache key missing for category " + categoryId);
            }

            if (remaining < 0) {
                redisTemplate.opsForValue().increment(key, quantity);
                log.debug("Sold out in Redis for category {}, remaining after rollback: {}", categoryId, remaining + quantity);
                return false;
            }

            return true;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis error on tryDecrement for category {}: {}. Tripping circuit breaker to protect DB from cascading failure.", categoryId, e.getMessage());
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Ticket inventory service is temporarily unavailable. Please try again later.");
        }
    }

    /**
     * Release reserved inventory back to Redis counter.
     */
    public void release(Long categoryId, int quantity) {
        String key = KEY_PREFIX + categoryId;
        try {
            Long newValue = redisTemplate.opsForValue().increment(key, quantity);
            log.debug("Released {} tickets for category {}, new Redis inventory: {}", quantity, categoryId, newValue);
        } catch (Exception e) {
            log.warn("Failed to release Redis inventory for category {}: {}", categoryId, e.getMessage());
        }
    }

    /**
     * Get real-time available quantity from Redis.
     */
    public Integer getAvailable(Long categoryId) {
        String key = KEY_PREFIX + categoryId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            log.warn("Failed to get available Redis inventory for category {}: {}", categoryId, e.getMessage());
            return null;
        }
    }

    /**
     * Check if Redis is healthy and reachable.
     */
    public boolean isAvailable() {
        try {
            return redisTemplate.getConnectionFactory() != null &&
                    Boolean.TRUE.equals(redisTemplate.hasKey("health_check_dummy_nonexistent"));
        } catch (Exception e) {
            return false;
        }
    }
}
