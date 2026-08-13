package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.catalog.TicketInventoryRepository;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherRedemption;
import com.geekup.eventticketbookingservice.voucher.VoucherRedemptionRepository;
import com.geekup.eventticketbookingservice.voucher.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final TicketInventoryRepository inventoryRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final VoucherRepository voucherRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processExpiredBookings() {
        log.info("Running expired bookings scheduler...");
        
        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(
                List.of(BookingStatus.RECEIVED, BookingStatus.PENDING_PAYMENT),
                ZonedDateTime.now()
        );

        for (Booking booking : expiredBookings) {
            try {
                expireBooking(booking);
            } catch (Exception e) {
                log.error("Failed to expire booking {}", booking.getId(), e);
            }
        }
        
        if (!expiredBookings.isEmpty()) {
            log.info("Expired {} bookings.", expiredBookings.size());
        }
    }

    private void expireBooking(Booking booking) {
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);


        // Release inventory
        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        for (BookingItem item : items) {
            TicketInventory inventory = inventoryRepository.findByIdForUpdate(item.getTicketCategoryId()).orElseThrow();
            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
            inventoryRepository.save(inventory);
        }

        // Revert voucher redemption if any
        if (booking.getVoucherId() != null) {
            Voucher voucher = voucherRepository.findById(booking.getVoucherId()).orElseThrow();
            voucher.setRedeemedCount(Math.max(0, voucher.getRedeemedCount() - 1));
            voucherRepository.save(voucher);
        }
    }
}
