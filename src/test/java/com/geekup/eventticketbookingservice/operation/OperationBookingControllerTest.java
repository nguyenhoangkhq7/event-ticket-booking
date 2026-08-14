package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.booking.Booking;
import com.geekup.eventticketbookingservice.booking.BookingStatus;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
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
                .subtotal(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("200.00"))
                .build();
    }

    @Test
    @DisplayName("getAllBookings returns 200 OK and list of bookings")
    void getAllBookings_Success() {
        when(operationService.getAllBookings()).thenReturn(List.of(testBooking));

        ResponseEntity<ApiResponse<List<Booking>>> response = operationBookingController.getAllBookings();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(1000L, response.getBody().getData().getFirst().getId());
        verify(operationService, times(1)).getAllBookings();
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
