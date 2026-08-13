package com.geekup.eventticketbookingservice.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Booking> findByUserId(Long userId);
    
    @Query("SELECT b FROM Booking b WHERE b.status IN :statuses AND b.expiresAt < :now")
    List<Booking> findExpiredBookings(List<BookingStatus> statuses, ZonedDateTime now);
}
