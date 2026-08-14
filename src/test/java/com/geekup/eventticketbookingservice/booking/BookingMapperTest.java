package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.booking.dto.BookingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookingMapperTest {

    private BookingMapper bookingMapper;

    @BeforeEach
    void setUp() {
        bookingMapper = new BookingMapperImpl();
    }

    @Test
    @DisplayName("toBookingResponse correctly maps Booking entity and BookingItems to BookingResponse")
    void toBookingResponse_MapsAllFields() {
        ZonedDateTime now = ZonedDateTime.now();
        Booking booking = Booking.builder()
                .id(100L)
                .bookingCode("BK-ABC12345")
                .userId(50L)
                .status(BookingStatus.RECEIVED)
                .subtotal(new BigDecimal("200.00"))
                .discountAmount(new BigDecimal("20.00"))
                .totalAmount(new BigDecimal("180.00"))
                .voucherId(10L)
                .expiresAt(now.plusMinutes(15))
                .createdAt(now)
                .build();

        BookingItem item1 = BookingItem.builder()
                .id(1L)
                .bookingId(100L)
                .ticketCategoryId(5L)
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        BookingItem item2 = BookingItem.builder()
                .id(2L)
                .bookingId(100L)
                .ticketCategoryId(6L)
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .build();

        BookingResponse response = bookingMapper.toBookingResponse(booking, List.of(item1, item2));

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("BK-ABC12345", response.getBookingCode());
        assertEquals(50L, response.getUserId());
        assertEquals(BookingStatus.RECEIVED, response.getStatus());
        assertEquals(new BigDecimal("200.00"), response.getSubtotal());
        assertEquals(new BigDecimal("20.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("180.00"), response.getTotalAmount());
        assertEquals(now.plusMinutes(15), response.getExpiresAt());
        assertEquals(now, response.getCreatedAt());

        assertNotNull(response.getItems());
        assertEquals(2, response.getItems().size());

        BookingResponse.BookingItemDto dto1 = response.getItems().get(0);
        assertEquals(5L, dto1.getTicketCategoryId());
        assertEquals(2, dto1.getQuantity());
        assertEquals(new BigDecimal("50.00"), dto1.getUnitPrice());
        assertEquals(0, new BigDecimal("100.00").compareTo(dto1.getSubtotal()));

        BookingResponse.BookingItemDto dto2 = response.getItems().get(1);
        assertEquals(6L, dto2.getTicketCategoryId());
        assertEquals(1, dto2.getQuantity());
        assertEquals(new BigDecimal("100.00"), dto2.getUnitPrice());
        assertEquals(0, new BigDecimal("100.00").compareTo(dto2.getSubtotal()));
    }

    @Test
    @DisplayName("toBookingItemDto calculates subtotal as unitPrice * quantity")
    void toBookingItemDto_CalculatesSubtotal() {
        BookingItem item = BookingItem.builder()
                .id(99L)
                .ticketCategoryId(12L)
                .quantity(3)
                .unitPrice(new BigDecimal("45.50"))
                .build();

        BookingResponse.BookingItemDto dto = bookingMapper.toBookingItemDto(item);

        assertNotNull(dto);
        assertEquals(12L, dto.getTicketCategoryId());
        assertEquals(3, dto.getQuantity());
        assertEquals(new BigDecimal("45.50"), dto.getUnitPrice());
        assertEquals(0, new BigDecimal("136.50").compareTo(dto.getSubtotal()));
    }

    @Test
    @DisplayName("toBookingResponse handles null booking or items")
    void toBookingResponse_NullHandling() {
        assertNull(bookingMapper.toBookingResponse(null, null));
        assertNull(bookingMapper.toBookingItemDto(null));

        Booking booking = Booking.builder().id(1L).build();
        BookingResponse response = bookingMapper.toBookingResponse(booking, null);
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertNull(response.getItems());
    }
}
