package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.catalog.dto.TicketCategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Catalog Entities, DTOs, and Enums Unit Tests")
class CatalogEntityAndDtoTest {

    @Nested
    @DisplayName("Concert Entity Tests")
    class ConcertEntityTests {

        @Test
        @DisplayName("Concert - Builder, Getters, and Setters")
        void testConcertEntity() {
            ZonedDateTime now = ZonedDateTime.now();
            Concert concert = Concert.builder()
                    .id(1L)
                    .name("Jazz Festival")
                    .description("Smooth jazz festival")
                    .venue("Grand Theater")
                    .startAt(now.plusDays(10))
                    .endAt(now.plusDays(10).plusHours(5))
                    .saleStartAt(now)
                    .saleEndAt(now.plusDays(9))
                    .status(ConcertStatus.PUBLISHED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertEquals(1L, concert.getId());
            assertEquals("Jazz Festival", concert.getName());
            assertEquals("Smooth jazz festival", concert.getDescription());
            assertEquals("Grand Theater", concert.getVenue());
            assertEquals(now.plusDays(10), concert.getStartAt());
            assertEquals(now.plusDays(10).plusHours(5), concert.getEndAt());
            assertEquals(now, concert.getSaleStartAt());
            assertEquals(now.plusDays(9), concert.getSaleEndAt());
            assertEquals(ConcertStatus.PUBLISHED, concert.getStatus());
            assertEquals(now, concert.getCreatedAt());
            assertEquals(now, concert.getUpdatedAt());

            // Test setters and no-args constructor
            Concert c2 = new Concert();
            c2.setId(2L);
            c2.setName("Rock Festival");
            c2.setDescription("Rock music");
            c2.setVenue("Open Air");
            c2.setStartAt(now);
            c2.setEndAt(now.plusHours(2));
            c2.setSaleStartAt(now.minusDays(1));
            c2.setSaleEndAt(now);
            c2.setStatus(ConcertStatus.DRAFT);
            c2.setCreatedAt(now);
            c2.setUpdatedAt(now);

            assertEquals(2L, c2.getId());
            assertEquals("Rock Festival", c2.getName());
            assertEquals(ConcertStatus.DRAFT, c2.getStatus());

            // All-args constructor
            Concert c3 = new Concert(3L, "Classical Gala", "Classical symphony", "Opera House",
                    now, now.plusHours(3), now.minusDays(2), now.minusDays(1), ConcertStatus.ENDED, now, now);
            assertEquals(3L, c3.getId());
            assertEquals(ConcertStatus.ENDED, c3.getStatus());
        }
    }

    @Nested
    @DisplayName("TicketCategory Entity Tests")
    class TicketCategoryEntityTests {

        @Test
        @DisplayName("TicketCategory - Builder, Getters, and Setters")
        void testTicketCategoryEntity() {
            ZonedDateTime now = ZonedDateTime.now();
            TicketCategory category = TicketCategory.builder()
                    .id(10L)
                    .concertId(1L)
                    .name("VIP Diamond")
                    .price(BigDecimal.valueOf(300.00))
                    .maxPerBooking(2)
                    .status(TicketCategoryStatus.ACTIVE)
                    .createdAt(now)
                    .build();

            assertEquals(10L, category.getId());
            assertEquals(1L, category.getConcertId());
            assertEquals("VIP Diamond", category.getName());
            assertEquals(BigDecimal.valueOf(300.00), category.getPrice());
            assertEquals(2, category.getMaxPerBooking());
            assertEquals(TicketCategoryStatus.ACTIVE, category.getStatus());
            assertEquals(now, category.getCreatedAt());

            // Test setters and constructors
            TicketCategory c2 = new TicketCategory();
            c2.setId(20L);
            c2.setConcertId(2L);
            c2.setName("Standard");
            c2.setPrice(BigDecimal.valueOf(50.00));
            c2.setMaxPerBooking(6);
            c2.setStatus(TicketCategoryStatus.INACTIVE);
            c2.setCreatedAt(now);

            assertEquals(20L, c2.getId());
            assertEquals(TicketCategoryStatus.INACTIVE, c2.getStatus());

            TicketCategory c3 = new TicketCategory(30L, 3L, "Economy", BigDecimal.valueOf(25.00), 10, TicketCategoryStatus.ACTIVE, now);
            assertEquals(30L, c3.getId());
        }
    }

    @Nested
    @DisplayName("TicketInventory Entity Tests")
    class TicketInventoryEntityTests {

        @Test
        @DisplayName("TicketInventory - Builder, Getters, and Setters")
        void testTicketInventoryEntity() {
            ZonedDateTime now = ZonedDateTime.now();
            TicketInventory inventory = TicketInventory.builder()
                    .ticketCategoryId(10L)
                    .totalQuantity(500)
                    .reservedQuantity(50)
                    .soldQuantity(150)
                    .updatedAt(now)
                    .build();

            assertEquals(10L, inventory.getTicketCategoryId());
            assertEquals(500, inventory.getTotalQuantity());
            assertEquals(50, inventory.getReservedQuantity());
            assertEquals(150, inventory.getSoldQuantity());
            assertEquals(now, inventory.getUpdatedAt());

            // Setters and constructors
            TicketInventory inv2 = new TicketInventory();
            inv2.setTicketCategoryId(20L);
            inv2.setTotalQuantity(200);
            inv2.setReservedQuantity(10);
            inv2.setSoldQuantity(90);
            inv2.setUpdatedAt(now);

            assertEquals(20L, inv2.getTicketCategoryId());
            assertEquals(200, inv2.getTotalQuantity());

            TicketInventory inv3 = new TicketInventory(30L, 100, 0, 0, now);
            assertEquals(30L, inv3.getTicketCategoryId());
            assertEquals(100, inv3.getTotalQuantity());
        }
    }

    @Nested
    @DisplayName("Catalog DTO Tests")
    class CatalogDtoTests {

        @Test
        @DisplayName("ConcertResponse - Builder, Getters, Equals, HashCode, ToString")
        void testConcertResponseDto() {
            ZonedDateTime now = ZonedDateTime.now();
            ConcertResponse res1 = ConcertResponse.builder()
                    .id(1L)
                    .name("Rock Night")
                    .description("Great show")
                    .venue("Stadium")
                    .startAt(now)
                    .endAt(now.plusHours(2))
                    .saleStartAt(now.minusDays(1))
                    .saleEndAt(now)
                    .status(ConcertStatus.PUBLISHED)
                    .build();

            assertEquals(1L, res1.getId());
            assertEquals("Rock Night", res1.getName());
            assertEquals("Stadium", res1.getVenue());
            assertEquals(ConcertStatus.PUBLISHED, res1.getStatus());

            ConcertResponse res2 = ConcertResponse.builder()
                    .id(1L)
                    .name("Rock Night")
                    .description("Great show")
                    .venue("Stadium")
                    .startAt(now)
                    .endAt(now.plusHours(2))
                    .saleStartAt(now.minusDays(1))
                    .saleEndAt(now)
                    .status(ConcertStatus.PUBLISHED)
                    .build();

            assertEquals(res1, res2);
            assertEquals(res1.hashCode(), res2.hashCode());
            assertTrue(res1.toString().contains("Rock Night"));
        }

        @Test
        @DisplayName("TicketCategoryResponse - Builder, Getters, Equals, HashCode, ToString")
        void testTicketCategoryResponseDto() {
            TicketCategoryResponse res1 = TicketCategoryResponse.builder()
                    .id(10L)
                    .concertId(1L)
                    .name("VIP")
                    .price(BigDecimal.valueOf(100.00))
                    .maxPerBooking(4)
                    .availableQuantity(50)
                    .build();

            assertEquals(10L, res1.getId());
            assertEquals(1L, res1.getConcertId());
            assertEquals("VIP", res1.getName());
            assertEquals(BigDecimal.valueOf(100.00), res1.getPrice());
            assertEquals(4, res1.getMaxPerBooking());
            assertEquals(50, res1.getAvailableQuantity());

            TicketCategoryResponse res2 = TicketCategoryResponse.builder()
                    .id(10L)
                    .concertId(1L)
                    .name("VIP")
                    .price(BigDecimal.valueOf(100.00))
                    .maxPerBooking(4)
                    .availableQuantity(50)
                    .build();

            assertEquals(res1, res2);
            assertEquals(res1.hashCode(), res2.hashCode());
            assertTrue(res1.toString().contains("VIP"));
        }
    }

    @Nested
    @DisplayName("Catalog Enum Tests")
    class CatalogEnumTests {

        @Test
        @DisplayName("ConcertStatus - All enum constants should be present")
        void testConcertStatus() {
            assertEquals(4, ConcertStatus.values().length);
            assertEquals(ConcertStatus.DRAFT, ConcertStatus.valueOf("DRAFT"));
            assertEquals(ConcertStatus.PUBLISHED, ConcertStatus.valueOf("PUBLISHED"));
            assertEquals(ConcertStatus.CANCELLED, ConcertStatus.valueOf("CANCELLED"));
            assertEquals(ConcertStatus.ENDED, ConcertStatus.valueOf("ENDED"));
        }

        @Test
        @DisplayName("TicketCategoryStatus - All enum constants should be present")
        void testTicketCategoryStatus() {
            assertEquals(2, TicketCategoryStatus.values().length);
            assertEquals(TicketCategoryStatus.ACTIVE, TicketCategoryStatus.valueOf("ACTIVE"));
            assertEquals(TicketCategoryStatus.INACTIVE, TicketCategoryStatus.valueOf("INACTIVE"));
        }
    }
}
