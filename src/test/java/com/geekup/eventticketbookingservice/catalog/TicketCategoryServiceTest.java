package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.TicketCategoryResponse;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketCategoryService Unit Tests")
class TicketCategoryServiceTest {

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @Mock
    private TicketInventoryRepository ticketInventoryRepository;

    @Mock
    private InventoryRedisService inventoryRedisService;

    @InjectMocks
    private TicketCategoryService ticketCategoryService;

    private TicketCategory categoryVip;
    private TicketCategory categoryGeneral;

    @BeforeEach
    void setUp() {
        categoryVip = TicketCategory.builder()
                .id(1L)
                .concertId(100L)
                .name("VIP")
                .price(BigDecimal.valueOf(200.00))
                .maxPerBooking(4)
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        categoryGeneral = TicketCategory.builder()
                .id(2L)
                .concertId(100L)
                .name("General Admission")
                .price(BigDecimal.valueOf(80.00))
                .maxPerBooking(6)
                .status(TicketCategoryStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("getCategoriesByConcertId() Tests")
    class GetCategoriesByConcertIdTests {

        @Test
        @DisplayName("Should use Redis available quantity when present in Redis cache")
        void getCategoriesByConcertId_RedisInventoryHit() {
            // Arrange
            when(ticketCategoryRepository.findByConcertIdAndStatus(100L, TicketCategoryStatus.ACTIVE))
                    .thenReturn(List.of(categoryVip));
            when(inventoryRedisService.getAvailable(1L)).thenReturn(45);

            // Act
            List<TicketCategoryResponse> results = ticketCategoryService.getCategoriesByConcertId(100L);

            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            TicketCategoryResponse response = results.get(0);
            assertEquals(1L, response.getId());
            assertEquals(100L, response.getConcertId());
            assertEquals("VIP", response.getName());
            assertEquals(BigDecimal.valueOf(200.00), response.getPrice());
            assertEquals(4, response.getMaxPerBooking());
            assertEquals(45, response.getAvailableQuantity());

            verify(inventoryRedisService, times(1)).getAvailable(1L);
            verify(ticketInventoryRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("Should fallback to database inventory calculation when Redis returns null")
        void getCategoriesByConcertId_RedisInventoryMiss_DbInventoryHit() {
            // Arrange
            TicketInventory inventory = TicketInventory.builder()
                    .ticketCategoryId(1L)
                    .totalQuantity(100)
                    .reservedQuantity(20)
                    .soldQuantity(30)
                    .build();

            when(ticketCategoryRepository.findByConcertIdAndStatus(100L, TicketCategoryStatus.ACTIVE))
                    .thenReturn(List.of(categoryVip));
            when(inventoryRedisService.getAvailable(1L)).thenReturn(null);
            when(ticketInventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));

            // Act
            List<TicketCategoryResponse> results = ticketCategoryService.getCategoriesByConcertId(100L);

            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            // 100 - 20 - 30 = 50
            assertEquals(50, results.get(0).getAvailableQuantity());

            verify(inventoryRedisService, times(1)).getAvailable(1L);
            verify(ticketInventoryRepository, times(1)).findById(1L);
        }

        @Test
        @DisplayName("Should clamp available quantity to 0 when reserved + sold exceeds total quantity in DB")
        void getCategoriesByConcertId_RedisInventoryMiss_NegativeDbInventoryClampedToZero() {
            // Arrange
            TicketInventory overbookedInventory = TicketInventory.builder()
                    .ticketCategoryId(1L)
                    .totalQuantity(50)
                    .reservedQuantity(30)
                    .soldQuantity(30) // total 60 > 50 -> -10
                    .build();

            when(ticketCategoryRepository.findByConcertIdAndStatus(100L, TicketCategoryStatus.ACTIVE))
                    .thenReturn(List.of(categoryVip));
            when(inventoryRedisService.getAvailable(1L)).thenReturn(null);
            when(ticketInventoryRepository.findById(1L)).thenReturn(Optional.of(overbookedInventory));

            // Act
            List<TicketCategoryResponse> results = ticketCategoryService.getCategoriesByConcertId(100L);

            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals(0, results.get(0).getAvailableQuantity());
        }

        @Test
        @DisplayName("Should set available quantity to 0 when Redis returns null and DB inventory not found")
        void getCategoriesByConcertId_RedisInventoryMiss_DbInventoryNotFound() {
            // Arrange
            when(ticketCategoryRepository.findByConcertIdAndStatus(100L, TicketCategoryStatus.ACTIVE))
                    .thenReturn(List.of(categoryVip));
            when(inventoryRedisService.getAvailable(1L)).thenReturn(null);
            when(ticketInventoryRepository.findById(1L)).thenReturn(Optional.empty());

            // Act
            List<TicketCategoryResponse> results = ticketCategoryService.getCategoriesByConcertId(100L);

            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals(0, results.get(0).getAvailableQuantity());
        }

        @Test
        @DisplayName("Should handle multiple categories with mixed Redis hit, DB hit, and DB miss")
        void getCategoriesByConcertId_MultipleCategoriesMixedSources() {
            // Arrange
            TicketCategory categoryStandard = TicketCategory.builder()
                    .id(3L)
                    .concertId(100L)
                    .name("Standard")
                    .price(BigDecimal.valueOf(50.00))
                    .maxPerBooking(8)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build();

            when(ticketCategoryRepository.findByConcertIdAndStatus(100L, TicketCategoryStatus.ACTIVE))
                    .thenReturn(List.of(categoryVip, categoryGeneral, categoryStandard));

            // Category 1 (VIP): Redis hit -> 30
            when(inventoryRedisService.getAvailable(1L)).thenReturn(30);

            // Category 2 (General): Redis miss -> DB hit (200 - 50 - 50 = 100)
            when(inventoryRedisService.getAvailable(2L)).thenReturn(null);
            when(ticketInventoryRepository.findById(2L)).thenReturn(Optional.of(
                    TicketInventory.builder()
                            .ticketCategoryId(2L)
                            .totalQuantity(200)
                            .reservedQuantity(50)
                            .soldQuantity(50)
                            .build()
            ));

            // Category 3 (Standard): Redis miss -> DB miss -> 0
            when(inventoryRedisService.getAvailable(3L)).thenReturn(null);
            when(ticketInventoryRepository.findById(3L)).thenReturn(Optional.empty());

            // Act
            List<TicketCategoryResponse> results = ticketCategoryService.getCategoriesByConcertId(100L);

            // Assert
            assertNotNull(results);
            assertEquals(3, results.size());

            assertEquals(1L, results.get(0).getId());
            assertEquals(30, results.get(0).getAvailableQuantity());

            assertEquals(2L, results.get(1).getId());
            assertEquals(100, results.get(1).getAvailableQuantity());

            assertEquals(3L, results.get(2).getId());
            assertEquals(0, results.get(2).getAvailableQuantity());
        }

        @Test
        @DisplayName("Should return empty list when no active ticket categories exist for concert")
        void getCategoriesByConcertId_EmptyList() {
            // Arrange
            when(ticketCategoryRepository.findByConcertIdAndStatus(100L, TicketCategoryStatus.ACTIVE))
                    .thenReturn(Collections.emptyList());

            // Act
            List<TicketCategoryResponse> results = ticketCategoryService.getCategoriesByConcertId(100L);

            // Assert
            assertNotNull(results);
            assertTrue(results.isEmpty());

            verify(inventoryRedisService, never()).getAvailable(any());
            verify(ticketInventoryRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("evictCategoryCache() Tests")
    class EvictCacheTests {

        @Test
        @DisplayName("Should execute evictCategoryCache without throwing exception")
        void evictCategoryCache_Success() {
            assertDoesNotThrow(() -> ticketCategoryService.evictCategoryCache(100L));
        }
    }
}
