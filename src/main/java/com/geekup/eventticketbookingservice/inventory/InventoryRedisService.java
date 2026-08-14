package com.geekup.eventticketbookingservice.inventory;

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
     * Try decrementing inventory atomically in Redis.
     * @return true if inventory deducted successfully or Redis is bypassed
     *         false if sold out in Redis
     */
    public boolean tryDecrement(Long categoryId, int quantity) {
        String key = KEY_PREFIX + categoryId;
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key, quantity);

            if (remaining == null) {
                log.warn("Inventory key not found in Redis for category {}. Falling back to DB.", categoryId);
                return true;
            }

            if (remaining < 0) {
                redisTemplate.opsForValue().increment(key, quantity);
                log.debug("Sold out in Redis for category {}, remaining after rollback: {}", categoryId, remaining + quantity);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Redis error on tryDecrement for category {}: {}. Falling back to DB.", categoryId, e.getMessage());
            return true; // Fallback to DB
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
