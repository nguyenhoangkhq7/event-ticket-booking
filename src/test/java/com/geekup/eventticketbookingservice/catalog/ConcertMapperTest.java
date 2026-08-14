package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.operation.dto.CreateConcertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConcertMapper Unit Tests")
class ConcertMapperTest {

    private ConcertMapper concertMapper;

    @BeforeEach
    void setUp() {
        concertMapper = Mappers.getMapper(ConcertMapper.class);
    }

    @Nested
    @DisplayName("toConcertResponse() Tests")
    class ToConcertResponseTests {

        @Test
        @DisplayName("Should correctly map Concert entity to ConcertResponse DTO")
        void toConcertResponse_ValidEntity_MapsAllFields() {
            ZonedDateTime now = ZonedDateTime.now();
            Concert concert = Concert.builder()
                    .id(1L)
                    .name("Coldplay World Tour")
                    .description("Music of the Spheres")
                    .venue("Wembley Stadium")
                    .startAt(now.plusDays(30))
                    .endAt(now.plusDays(30).plusHours(3))
                    .saleStartAt(now.minusDays(5))
                    .saleEndAt(now.plusDays(25))
                    .status(ConcertStatus.PUBLISHED)
                    .build();

            ConcertResponse response = concertMapper.toConcertResponse(concert);

            assertNotNull(response);
            assertEquals(1L, response.getId());
            assertEquals("Coldplay World Tour", response.getName());
            assertEquals("Music of the Spheres", response.getDescription());
            assertEquals("Wembley Stadium", response.getVenue());
            assertEquals(concert.getStartAt(), response.getStartAt());
            assertEquals(concert.getEndAt(), response.getEndAt());
            assertEquals(concert.getSaleStartAt(), response.getSaleStartAt());
            assertEquals(concert.getSaleEndAt(), response.getSaleEndAt());
            assertEquals(ConcertStatus.PUBLISHED, response.getStatus());
        }

        @Test
        @DisplayName("Should return null when Concert entity is null")
        void toConcertResponse_NullInput_ReturnsNull() {
            assertNull(concertMapper.toConcertResponse(null));
        }
    }

    @Nested
    @DisplayName("toConcert() Tests")
    class ToConcertTests {

        @Test
        @DisplayName("Should correctly map CreateConcertRequest to Concert entity with DRAFT status and ignored ID")
        void toConcert_ValidRequest_MapsFieldsAndSetsDraftStatus() {
            ZonedDateTime now = ZonedDateTime.now();
            CreateConcertRequest request = new CreateConcertRequest();
            request.setName("Taylor Swift Eras Tour");
            request.setDescription("The Eras Tour Concert");
            request.setVenue("SoFi Stadium");
            request.setStartAt(now.plusDays(60));
            request.setEndAt(now.plusDays(60).plusHours(4));
            request.setSaleStartAt(now.plusDays(10));
            request.setSaleEndAt(now.plusDays(50));

            Concert concert = concertMapper.toConcert(request);

            assertNotNull(concert);
            assertNull(concert.getId()); // Ignored target
            assertEquals("Taylor Swift Eras Tour", concert.getName());
            assertEquals("The Eras Tour Concert", concert.getDescription());
            assertEquals("SoFi Stadium", concert.getVenue());
            assertEquals(request.getStartAt(), concert.getStartAt());
            assertEquals(request.getEndAt(), concert.getEndAt());
            assertEquals(request.getSaleStartAt(), concert.getSaleStartAt());
            assertEquals(request.getSaleEndAt(), concert.getSaleEndAt());
            assertEquals(ConcertStatus.DRAFT, concert.getStatus()); // Constant mapping
        }

        @Test
        @DisplayName("Should return null when CreateConcertRequest is null")
        void toConcert_NullInput_ReturnsNull() {
            assertNull(concertMapper.toConcert(null));
        }
    }
}
