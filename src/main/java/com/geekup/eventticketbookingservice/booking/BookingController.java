package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.booking.dto.BookingResponse;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.createBooking(user.getId(), request, idempotencyKey)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getUserBookings(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getUserBookings(user.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBooking(id, user.getId())));
    }

    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<ApiResponse<BookingResponse>> confirmPayment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.confirmPayment(id, user.getId())));
    }
}
