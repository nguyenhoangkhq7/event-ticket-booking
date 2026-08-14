package com.geekup.eventticketbookingservice.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Booking> findByUserId(Long userId);
    
    @Query("SELECT b FROM Booking b WHERE b.status IN :statuses AND b.expiresAt < :now")
    List<Booking> findExpiredBookings(@Param("statuses") List<BookingStatus> statuses, @Param("now") ZonedDateTime now);

    @Query("SELECT b FROM Booking b WHERE (:status IS NULL OR b.status = :status) AND (:riskStatus IS NULL OR b.riskStatus = :riskStatus) ORDER BY b.createdAt DESC")
    List<Booking> findBookingsWithFilters(@Param("status") BookingStatus status, @Param("riskStatus") RiskStatus riskStatus);
}
