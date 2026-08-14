package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.booking.dto.BookingItemRequest;
import com.geekup.eventticketbookingservice.booking.dto.BookingResponse;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
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
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private User authUser;
    private BookingResponse sampleBookingResponse;

    @BeforeEach
    void setUp() {
        authUser = User.builder()
                .id(42L)
                .email("john.doe@example.com")
                .role(Role.CUSTOMER)
                .build();

        sampleBookingResponse = BookingResponse.builder()
                .id(1000L)
                .bookingCode("BK-1000")
                .userId(42L)
                .status(BookingStatus.RECEIVED)
                .subtotal(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    @DisplayName("createBooking returns 200 OK with BookingResponse inside ApiResponse")
    void createBooking_ReturnsSuccess() {
        BookingItemRequest item = new BookingItemRequest();
        item.setTicketCategoryId(1L);
        item.setQuantity(2);

        CreateBookingRequest request = new CreateBookingRequest();
        request.setItems(List.of(item));
        request.setVoucherCode("PROMO10");

        String idempotencyKey = "idem-uuid-12345";

        when(bookingService.createBooking(42L, request, idempotencyKey)).thenReturn(sampleBookingResponse);

        ResponseEntity<ApiResponse<BookingResponse>> response =
                bookingController.createBooking(authUser, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(sampleBookingResponse, response.getBody().getData());
        verify(bookingService, times(1)).createBooking(42L, request, idempotencyKey);
    }

    @Test
    @DisplayName("getUserBookings returns 200 OK with list of BookingResponse")
    void getUserBookings_ReturnsSuccess() {
        when(bookingService.getUserBookings(42L)).thenReturn(List.of(sampleBookingResponse));

        ResponseEntity<ApiResponse<List<BookingResponse>>> response =
                bookingController.getUserBookings(authUser);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(1000L, response.getBody().getData().getFirst().getId());
        verify(bookingService, times(1)).getUserBookings(42L);
    }

    @Test
    @DisplayName("getBooking returns 200 OK with BookingResponse for specified booking id")
    void getBooking_ReturnsSuccess() {
        when(bookingService.getBooking(1000L, 42L)).thenReturn(sampleBookingResponse);

        ResponseEntity<ApiResponse<BookingResponse>> response =
                bookingController.getBooking(authUser, 1000L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1000L, response.getBody().getData().getId());
        verify(bookingService, times(1)).getBooking(1000L, 42L);
    }

    @Test
    @DisplayName("confirmPayment returns 200 OK with updated BookingResponse")
    void confirmPayment_ReturnsSuccess() {
        BookingResponse paidResponse = BookingResponse.builder()
                .id(1000L)
                .bookingCode("BK-1000")
                .userId(42L)
                .status(BookingStatus.PAID)
                .build();

        when(bookingService.confirmPayment(1000L, 42L)).thenReturn(paidResponse);

        ResponseEntity<ApiResponse<BookingResponse>> response =
                bookingController.confirmPayment(authUser, 1000L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(BookingStatus.PAID, response.getBody().getData().getStatus());
        verify(bookingService, times(1)).confirmPayment(1000L, 42L);
    }
}
