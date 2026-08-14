package com.geekup.eventticketbookingservice.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingExpiryService bookingExpiryService;

    @Scheduled(fixedDelay = 60_000)
    public void processExpiredBookings() {
        log.info("Running expired bookings scheduler...");
        
        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(
                List.of(BookingStatus.RECEIVED, BookingStatus.PENDING_PAYMENT),
                ZonedDateTime.now()
        );

        for (Booking booking : expiredBookings) {
            try {
                bookingExpiryService.expireBooking(booking);
            } catch (Exception e) {
                log.error("Failed to expire booking {}", booking.getId(), e);
            }
        }
        
        if (!expiredBookings.isEmpty()) {
            log.info("Expired {} bookings.", expiredBookings.size());
        }
    }
}
