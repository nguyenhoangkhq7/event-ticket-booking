package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConcertService Unit Tests")
class ConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private ConcertMapper concertMapper;

    @InjectMocks
    private ConcertService concertService;

    private Concert testConcert;
    private ConcertResponse testConcertResponse;

    @BeforeEach
    void setUp() {
        testConcert = Concert.builder()
                .id(1L)
                .name("Summer Fest 2026")
                .description("Big summer festival")
                .venue("My Dinh Stadium")
                .startAt(ZonedDateTime.now().plusDays(20))
                .endAt(ZonedDateTime.now().plusDays(20).plusHours(4))
                .saleStartAt(ZonedDateTime.now().minusDays(5))
                .saleEndAt(ZonedDateTime.now().plusDays(15))
                .status(ConcertStatus.PUBLISHED)
                .build();

        testConcertResponse = ConcertResponse.builder()
                .id(1L)
                .name("Summer Fest 2026")
                .description("Big summer festival")
                .venue("My Dinh Stadium")
                .startAt(testConcert.getStartAt())
                .endAt(testConcert.getEndAt())
                .saleStartAt(testConcert.getSaleStartAt())
                .saleEndAt(testConcert.getSaleEndAt())
                .status(ConcertStatus.PUBLISHED)
                .build();
    }

    @Nested
    @DisplayName("getPublishedConcerts() Tests")
    class GetPublishedConcertsTests {

        @Test
        @DisplayName("Should return page of published concerts mapped to DTOs")
        void getPublishedConcerts_Success() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Concert> concertPage = new PageImpl<>(List.of(testConcert), pageable, 1);
            when(concertRepository.findByStatus(ConcertStatus.PUBLISHED, pageable)).thenReturn(concertPage);
            when(concertMapper.toConcertResponse(testConcert)).thenReturn(testConcertResponse);

            // Act
            Page<ConcertResponse> result = concertService.getPublishedConcerts(pageable);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
            assertEquals("Summer Fest 2026", result.getContent().get(0).getName());
            assertEquals(ConcertStatus.PUBLISHED, result.getContent().get(0).getStatus());

            verify(concertRepository, times(1)).findByStatus(ConcertStatus.PUBLISHED, pageable);
            verify(concertMapper, times(1)).toConcertResponse(testConcert);
        }

        @Test
        @DisplayName("Should return empty page when no concerts are published")
        void getPublishedConcerts_EmptyList() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Concert> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            when(concertRepository.findByStatus(ConcertStatus.PUBLISHED, pageable)).thenReturn(emptyPage);

            // Act
            Page<ConcertResponse> result = concertService.getPublishedConcerts(pageable);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEquals(0, result.getTotalElements());

            verify(concertRepository, times(1)).findByStatus(ConcertStatus.PUBLISHED, pageable);
            verify(concertMapper, never()).toConcertResponse(any());
        }
    }

    @Nested
    @DisplayName("getConcertById() Tests")
    class GetConcertByIdTests {

        @Test
        @DisplayName("Should return concert response when concert exists")
        void getConcertById_Success() {
            // Arrange
            when(concertRepository.findById(1L)).thenReturn(Optional.of(testConcert));
            when(concertMapper.toConcertResponse(testConcert)).thenReturn(testConcertResponse);

            // Act
            ConcertResponse result = concertService.getConcertById(1L);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("Summer Fest 2026", result.getName());
            assertEquals("My Dinh Stadium", result.getVenue());

            verify(concertRepository, times(1)).findById(1L);
            verify(concertMapper, times(1)).toConcertResponse(testConcert);
        }

        @Test
        @DisplayName("Should throw AppException with CONCERT_NOT_FOUND when concert does not exist")
        void getConcertById_NotFound_ThrowsAppException() {
            // Arrange
            when(concertRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            AppException exception = assertThrows(
                    AppException.class,
                    () -> concertService.getConcertById(999L)
            );
            assertEquals(ErrorCode.CONCERT_NOT_FOUND, exception.getErrorCode());
            assertEquals("Concert not found", exception.getMessage());

            verify(concertRepository, times(1)).findById(999L);
            verify(concertMapper, never()).toConcertResponse(any());
        }
    }

    @Nested
    @DisplayName("evictConcertCache() Tests")
    class EvictCacheTests {

        @Test
        @DisplayName("Should execute evictConcertCache without error")
        void evictConcertCache_Success() {
            assertDoesNotThrow(() -> concertService.evictConcertCache());
        }
    }
}
