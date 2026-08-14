package com.geekup.eventticketbookingservice.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.*;

class CacheConfigTest {

    @Test
    @DisplayName("cacheManager registers expected caches: concerts, concert, ticketCategories")
    void cacheManager_RegistersCustomCaches() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheManager cacheManager = cacheConfig.cacheManager();

        assertNotNull(cacheManager);

        Cache concertsCache = cacheManager.getCache("concerts");
        assertNotNull(concertsCache, "concerts cache should be configured");

        Cache concertCache = cacheManager.getCache("concert");
        assertNotNull(concertCache, "concert cache should be configured");

        Cache ticketCategoriesCache = cacheManager.getCache("ticketCategories");
        assertNotNull(ticketCategoriesCache, "ticketCategories cache should be configured");
    }

    @Test
    @DisplayName("cacheManager allows storing and retrieving cached values")
    void cacheManager_StoresAndRetrievesValues() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheManager cacheManager = cacheConfig.cacheManager();

        Cache concertsCache = cacheManager.getCache("concerts");
        assertNotNull(concertsCache);

        concertsCache.put("all", "concert_list_payload");
        Cache.ValueWrapper wrapper = concertsCache.get("all");
        assertNotNull(wrapper);
        assertEquals("concert_list_payload", wrapper.get());

        concertsCache.evict("all");
        assertNull(concertsCache.get("all"));
    }
}
