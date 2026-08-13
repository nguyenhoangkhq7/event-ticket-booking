package com.geekup.eventticketbookingservice.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    List<BookingItem> findByBookingId(Long bookingId);
}
