package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.booking.Booking;
import com.geekup.eventticketbookingservice.booking.BookingStatus;
import com.geekup.eventticketbookingservice.booking.RiskStatus;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingRiskStatusRequest;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingStatusRequest;
import com.geekup.eventticketbookingservice.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operation/bookings")
@RequiredArgsConstructor
public class OperationBookingController {

    private final OperationService operationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Booking>>> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) RiskStatus riskStatus
    ) {
        return ResponseEntity.ok(ApiResponse.success(operationService.getAllBookings(status, riskStatus)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Booking>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingStatusRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(operationService.updateBookingStatus(id, request, admin.getId())));
    }

    @PatchMapping("/{id}/risk-status")
    public ResponseEntity<ApiResponse<Booking>> updateRiskStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingRiskStatusRequest request,
            @AuthenticationPrincipal User admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(operationService.updateBookingRiskStatus(id, request, admin.getId())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin
    ) {
        operationService.cancelBookingAndReleaseInventory(id, admin.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
