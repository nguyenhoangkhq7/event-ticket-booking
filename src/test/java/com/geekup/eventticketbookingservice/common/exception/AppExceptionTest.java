package com.geekup.eventticketbookingservice.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class AppExceptionTest {

    @Test
    @DisplayName("AppException constructor with ErrorCode uses default ErrorCode message")
    void constructorWithErrorCode_UsesDefaultMessage() {
        AppException exception = new AppException(ErrorCode.BOOKING_NOT_FOUND);

        assertEquals(ErrorCode.BOOKING_NOT_FOUND, exception.getErrorCode());
        assertEquals("Booking not found", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getErrorCode().getStatus());
        assertEquals("BOOKING_NOT_FOUND", exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("AppException constructor with ErrorCode and custom message uses custom message")
    void constructorWithCustomMessage_UsesCustomMessage() {
        String customMsg = "Booking #12345 does not exist in our records";
        AppException exception = new AppException(ErrorCode.BOOKING_NOT_FOUND, customMsg);

        assertEquals(ErrorCode.BOOKING_NOT_FOUND, exception.getErrorCode());
        assertEquals(customMsg, exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("Verify all ErrorCode enum values have non-null code, message, and status")
    void errorCode_AllValuesHaveNonNullFields(ErrorCode errorCode) {
        assertNotNull(errorCode.getCode());
        assertFalse(errorCode.getCode().isBlank());
        assertNotNull(errorCode.getMessage());
        assertFalse(errorCode.getMessage().isBlank());
        assertNotNull(errorCode.getStatus());
    }

    @Test
    @DisplayName("Verify specific ErrorCode status codes")
    void errorCode_SpecificStatuses() {
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND.getStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS.getStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.CONCERT_NOT_FOUND.getStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.TICKET_CATEGORY_NOT_FOUND.getStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.TICKET_SOLD_OUT.getStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.NOT_ENOUGH_TICKETS.getStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.VOUCHER_NOT_FOUND.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VOUCHER_INVALID.getStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.VOUCHER_ALREADY_REDEEMED.getStatus());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.VOUCHER_LIMIT_REACHED.getStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_BOOKING_STATUS.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.getStatus());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getStatus());
    }
}
