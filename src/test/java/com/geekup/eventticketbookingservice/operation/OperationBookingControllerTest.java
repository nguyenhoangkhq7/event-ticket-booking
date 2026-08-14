package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.booking.Booking;
import com.geekup.eventticketbookingservice.booking.BookingStatus;
import com.geekup.eventticketbookingservice.booking.RiskStatus;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingRiskStatusRequest;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingStatusRequest;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationBookingController Unit Tests")
class OperationBookingControllerTest {

    @Mock
    private OperationService operationService;

    @InjectMocks
    private OperationBookingController operationBookingController;

    private User adminUser;
    private Booking testBooking;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(99L)
                .email("admin@eventhub.com")
                .role(Role.ADMIN)
                .build();

        testBooking = Booking.builder()
                .id(1000L)
                .bookingCode("BK-1000")
                .userId(5L)
                .status(BookingStatus.RECEIVED)
                .riskStatus(RiskStatus.NORMAL)
                .subtotal(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("200.00"))
                .build();
    }

    @Test
    @DisplayName("getAllBookings returns 200 OK and list of bookings without filters")
    void getAllBookings_NoFilters_Success() {
        when(operationService.getAllBookings(null, null)).thenReturn(List.of(testBooking));

        ResponseEntity<ApiResponse<List<Booking>>> response = operationBookingController.getAllBookings(null, null);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(1000L, response.getBody().getData().getFirst().getId());
        verify(operationService, times(1)).getAllBookings(null, null);
    }

    @Test
    @DisplayName("getAllBookings returns 200 OK with status and riskStatus filters")
    void getAllBookings_WithFilters_Success() {
        testBooking.setRiskStatus(RiskStatus.SUSPICIOUS);
        testBooking.setStatus(BookingStatus.FAILED);
        when(operationService.getAllBookings(BookingStatus.FAILED, RiskStatus.SUSPICIOUS)).thenReturn(List.of(testBooking));

        ResponseEntity<ApiResponse<List<Booking>>> response = operationBookingController.getAllBookings(BookingStatus.FAILED, RiskStatus.SUSPICIOUS);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(RiskStatus.SUSPICIOUS, response.getBody().getData().getFirst().getRiskStatus());
        assertEquals(BookingStatus.FAILED, response.getBody().getData().getFirst().getStatus());
        verify(operationService, times(1)).getAllBookings(BookingStatus.FAILED, RiskStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("updateStatus returns 200 OK and updated booking")
    void updateStatus_Success() {
        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.PAID);
        request.setReason("Admin manual confirmation");

        Booking confirmedBooking = Booking.builder()
                .id(1000L)
                .bookingCode("BK-1000")
                .status(BookingStatus.PAID)
                .build();

        when(operationService.updateBookingStatus(1000L, request, 99L)).thenReturn(confirmedBooking);

        ResponseEntity<ApiResponse<Booking>> response = operationBookingController.updateStatus(1000L, request, adminUser);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(BookingStatus.PAID, response.getBody().getData().getStatus());
        verify(operationService, times(1)).updateBookingStatus(1000L, request, 99L);
    }

    @Test
    @DisplayName("updateRiskStatus returns 200 OK and updated booking")
    void updateRiskStatus_Success() {
        UpdateBookingRiskStatusRequest request = UpdateBookingRiskStatusRequest.builder()
                .riskStatus(RiskStatus.SUSPICIOUS)
                .reason("Multiple failed attempts detected")
                .build();

        Booking suspiciousBooking = Booking.builder()
                .id(1000L)
                .bookingCode("BK-1000")
                .riskStatus(RiskStatus.SUSPICIOUS)
                .build();

        when(operationService.updateBookingRiskStatus(1000L, request, 99L)).thenReturn(suspiciousBooking);

        ResponseEntity<ApiResponse<Booking>> response = operationBookingController.updateRiskStatus(1000L, request, adminUser);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(RiskStatus.SUSPICIOUS, response.getBody().getData().getRiskStatus());
        verify(operationService, times(1)).updateBookingRiskStatus(1000L, request, 99L);
    }

    @Test
    @DisplayName("cancelBooking returns 200 OK and calls service cancelBookingAndReleaseInventory")
    void cancelBooking_Success() {
        doNothing().when(operationService).cancelBookingAndReleaseInventory(1000L, 99L);

        ResponseEntity<ApiResponse<Void>> response = operationBookingController.cancelBooking(1000L, adminUser);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNull(response.getBody().getData());
        verify(operationService, times(1)).cancelBookingAndReleaseInventory(1000L, 99L);
    }
}
