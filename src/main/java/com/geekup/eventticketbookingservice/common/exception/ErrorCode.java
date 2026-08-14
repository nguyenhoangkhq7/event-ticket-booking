package com.geekup.eventticketbookingservice.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email is already in use", HttpStatus.CONFLICT),
    CONCERT_NOT_FOUND("CONCERT_NOT_FOUND", "Concert not found", HttpStatus.NOT_FOUND),
    TICKET_CATEGORY_NOT_FOUND("TICKET_CATEGORY_NOT_FOUND", "Ticket category not found", HttpStatus.NOT_FOUND),
    TICKET_SOLD_OUT("TICKET_SOLD_OUT", "Ticket sold out", HttpStatus.CONFLICT),
    NOT_ENOUGH_TICKETS("NOT_ENOUGH_TICKETS", "Not enough tickets available", HttpStatus.CONFLICT),
    VOUCHER_NOT_FOUND("VOUCHER_NOT_FOUND", "Voucher not found", HttpStatus.NOT_FOUND),
    VOUCHER_INVALID("VOUCHER_INVALID", "Voucher is invalid or expired", HttpStatus.BAD_REQUEST),
    VOUCHER_ALREADY_REDEEMED("VOUCHER_ALREADY_REDEEMED", "Voucher already redeemed by this user", HttpStatus.CONFLICT),
    VOUCHER_LIMIT_REACHED("VOUCHER_LIMIT_REACHED", "Voucher redemption limit reached", HttpStatus.CONFLICT),
    BOOKING_NOT_FOUND("BOOKING_NOT_FOUND", "Booking not found", HttpStatus.NOT_FOUND),
    INVALID_BOOKING_STATUS("INVALID_BOOKING_STATUS", "Invalid booking status transition", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized access", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Forbidden access", HttpStatus.FORBIDDEN),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
