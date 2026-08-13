package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.booking.dto.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "items", source = "items")
    BookingResponse toBookingResponse(Booking booking, List<BookingItem> items);

    @Mapping(target = "subtotal", expression = "java(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    BookingResponse.BookingItemDto toBookingItemDto(BookingItem item);
}
