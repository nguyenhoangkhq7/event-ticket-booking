package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.catalog.Concert;
import com.geekup.eventticketbookingservice.catalog.ConcertStatus;
import com.geekup.eventticketbookingservice.catalog.TicketCategory;
import com.geekup.eventticketbookingservice.catalog.TicketCategoryStatus;
import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.operation.dto.CreateConcertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationConcertController Unit Tests")
class OperationConcertControllerTest {

    @Mock
    private OperationService operationService;

    @InjectMocks
    private OperationConcertController operationConcertController;

    private Concert testConcert;
    private TicketCategory testCategory;
    private TicketInventory testInventory;

    @BeforeEach
    void setUp() {
        testConcert = Concert.builder()
                .id(1L)
                .name("Rock Festival")
                .venue("Main Arena")
                .startAt(ZonedDateTime.now().plusDays(5))
                .saleStartAt(ZonedDateTime.now().minusDays(1))
                .saleEndAt(ZonedDateTime.now().plusDays(4))
                .status(ConcertStatus.DRAFT)
                .build();

        testCategory = TicketCategory.builder()
                .id(10L)
                .concertId(1L)
                .name("VIP")
                .price(new BigDecimal("150.00"))
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        testInventory = TicketInventory.builder()
                .ticketCategoryId(10L)
                .totalQuantity(500)
                .reservedQuantity(0)
                .soldQuantity(0)
                .build();
    }

    @Test
    @DisplayName("createConcert returns 200 OK and created concert")
    void createConcert_Success() {
        CreateConcertRequest request = new CreateConcertRequest();
        request.setName("Rock Festival");
        request.setVenue("Main Arena");

        when(operationService.createConcert(request)).thenReturn(testConcert);

        ResponseEntity<ApiResponse<Concert>> response = operationConcertController.createConcert(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Rock Festival", response.getBody().getData().getName());
        verify(operationService, times(1)).createConcert(request);
    }

    @Test
    @DisplayName("publishConcert returns 200 OK and published concert")
    void publishConcert_Success() {
        testConcert.setStatus(ConcertStatus.PUBLISHED);
        when(operationService.publishConcert(1L)).thenReturn(testConcert);

        ResponseEntity<ApiResponse<Concert>> response = operationConcertController.publishConcert(1L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(ConcertStatus.PUBLISHED, response.getBody().getData().getStatus());
        verify(operationService, times(1)).publishConcert(1L);
    }

    @Test
    @DisplayName("addTicketCategory returns 200 OK and added ticket category")
    void addTicketCategory_Success() {
        when(operationService.addTicketCategory(1L, testCategory)).thenReturn(testCategory);

        ResponseEntity<ApiResponse<TicketCategory>> response = operationConcertController.addTicketCategory(1L, testCategory);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(10L, response.getBody().getData().getId());
        assertEquals("VIP", response.getBody().getData().getName());
        verify(operationService, times(1)).addTicketCategory(1L, testCategory);
    }

    @Test
    @DisplayName("setInventory returns 200 OK and updated ticket inventory")
    void setInventory_Success() {
        when(operationService.setInventory(10L, 500)).thenReturn(testInventory);

        ResponseEntity<ApiResponse<TicketInventory>> response = operationConcertController.setInventory(1L, 10L, 500);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(500, response.getBody().getData().getTotalQuantity());
        verify(operationService, times(1)).setInventory(10L, 500);
    }
}
