package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.catalog.dto.TicketCategoryResponse;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConcertController Unit Tests")
class ConcertControllerTest {

    @Mock
    private ConcertService concertService;

    @Mock
    private TicketCategoryService ticketCategoryService;

    @InjectMocks
    private ConcertController concertController;

    private ConcertResponse sampleConcertResponse;
    private TicketCategoryResponse sampleCategoryResponse;

    @BeforeEach
    void setUp() {
        sampleConcertResponse = ConcertResponse.builder()
                .id(1L)
                .name("Rock Night 2026")
                .description("An amazing rock concert")
                .venue("National Stadium")
                .startAt(ZonedDateTime.now().plusDays(10))
                .endAt(ZonedDateTime.now().plusDays(10).plusHours(3))
                .saleStartAt(ZonedDateTime.now().minusDays(1))
                .saleEndAt(ZonedDateTime.now().plusDays(9))
                .status(ConcertStatus.PUBLISHED)
                .build();

        sampleCategoryResponse = TicketCategoryResponse.builder()
                .id(10L)
                .concertId(1L)
                .name("VIP")
                .price(BigDecimal.valueOf(150.00))
                .maxPerBooking(4)
                .availableQuantity(50)
                .build();
    }

    @Nested
    @DisplayName("GET /api/concerts")
    class GetPublishedConcertsTests {

        @Test
        @DisplayName("Should return page of published concerts with HTTP 200")
        void getPublishedConcerts_Success() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<ConcertResponse> page = new PageImpl<>(List.of(sampleConcertResponse), pageable, 1);
            when(concertService.getPublishedConcerts(pageable)).thenReturn(page);

            // Act
            ResponseEntity<ApiResponse<Page<ConcertResponse>>> response = concertController.getPublishedConcerts(pageable);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1, response.getBody().getData().getTotalElements());
            assertEquals(1, response.getBody().getData().getContent().size());
            assertEquals(sampleConcertResponse.getId(), response.getBody().getData().getContent().get(0).getId());
            assertEquals("Rock Night 2026", response.getBody().getData().getContent().get(0).getName());

            verify(concertService, times(1)).getPublishedConcerts(pageable);
        }

        @Test
        @DisplayName("Should return empty page with HTTP 200 when no published concerts exist")
        void getPublishedConcerts_EmptyList() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<ConcertResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            when(concertService.getPublishedConcerts(pageable)).thenReturn(emptyPage);

            // Act
            ResponseEntity<ApiResponse<Page<ConcertResponse>>> response = concertController.getPublishedConcerts(pageable);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData().getContent().isEmpty());
            assertEquals(0, response.getBody().getData().getTotalElements());

            verify(concertService, times(1)).getPublishedConcerts(pageable);
        }
    }

    @Nested
    @DisplayName("GET /api/concerts/{id}")
    class GetConcertByIdTests {

        @Test
        @DisplayName("Should return concert by ID with HTTP 200")
        void getConcertById_Success() {
            // Arrange
            when(concertService.getConcertById(1L)).thenReturn(sampleConcertResponse);

            // Act
            ResponseEntity<ApiResponse<ConcertResponse>> response = concertController.getConcertById(1L);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1L, response.getBody().getData().getId());
            assertEquals("Rock Night 2026", response.getBody().getData().getName());

            verify(concertService, times(1)).getConcertById(1L);
        }

        @Test
        @DisplayName("Should propagate AppException when concert is not found")
        void getConcertById_NotFound_ThrowsException() {
            // Arrange
            when(concertService.getConcertById(999L)).thenThrow(new AppException(ErrorCode.CONCERT_NOT_FOUND));

            // Act & Assert
            AppException exception = assertThrows(
                    AppException.class,
                    () -> concertController.getConcertById(999L)
            );
            assertEquals(ErrorCode.CONCERT_NOT_FOUND, exception.getErrorCode());
            verify(concertService, times(1)).getConcertById(999L);
        }
    }

    @Nested
    @DisplayName("GET /api/concerts/{id}/ticket-categories")
    class GetTicketCategoriesTests {

        @Test
        @DisplayName("Should return ticket categories for concert with HTTP 200")
        void getTicketCategories_Success() {
            // Arrange
            when(ticketCategoryService.getCategoriesByConcertId(1L)).thenReturn(List.of(sampleCategoryResponse));

            // Act
            ResponseEntity<ApiResponse<List<TicketCategoryResponse>>> response = concertController.getTicketCategories(1L);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1, response.getBody().getData().size());
            assertEquals("VIP", response.getBody().getData().get(0).getName());
            assertEquals(50, response.getBody().getData().get(0).getAvailableQuantity());

            verify(ticketCategoryService, times(1)).getCategoriesByConcertId(1L);
        }

        @Test
        @DisplayName("Should return empty list when concert has no ticket categories")
        void getTicketCategories_EmptyList() {
            // Arrange
            when(ticketCategoryService.getCategoriesByConcertId(1L)).thenReturn(Collections.emptyList());

            // Act
            ResponseEntity<ApiResponse<List<TicketCategoryResponse>>> response = concertController.getTicketCategories(1L);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData().isEmpty());

            verify(ticketCategoryService, times(1)).getCategoriesByConcertId(1L);
        }
    }
}
