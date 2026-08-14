package com.geekup.eventticketbookingservice.booking.dto;

import com.geekup.eventticketbookingservice.booking.BookingStatus;
import com.geekup.eventticketbookingservice.booking.RiskStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private String bookingCode;
    private Long userId;
    private BookingStatus status;
    private RiskStatus riskStatus;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private ZonedDateTime expiresAt;
    private ZonedDateTime createdAt;
    private List<BookingItemDto> items;

    @Data
    @Builder
    public static class BookingItemDto {
        private Long ticketCategoryId;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
