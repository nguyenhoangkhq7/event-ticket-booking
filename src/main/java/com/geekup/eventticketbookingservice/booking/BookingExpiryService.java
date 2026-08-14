package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.catalog.TicketInventoryRepository;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherRedemptionRepository;
import com.geekup.eventticketbookingservice.voucher.VoucherRepository;
import com.geekup.eventticketbookingservice.voucher.VoucherStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final TicketInventoryRepository inventoryRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final VoucherRepository voucherRepository;
    private final InventoryRedisService inventoryRedisService;

    /**
     * REQUIRES_NEW: each booking expires in its own independent transaction.
     * An error on one expired booking will not roll back others in the batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireBooking(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        // 1. Release inventory (DB & Redis)
        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        for (BookingItem item : items) {
            TicketInventory inventory = inventoryRepository
                    .findByIdForUpdate(item.getTicketCategoryId()).orElseThrow();
            inventory.setReservedQuantity(
                    Math.max(0, inventory.getReservedQuantity() - item.getQuantity())
            );
            inventoryRepository.save(inventory);

            // Release Redis counter
            inventoryRedisService.release(item.getTicketCategoryId(), item.getQuantity());
        }

        // 2. Revert voucher - delete redemption record and decrement count
        if (booking.getVoucherId() != null) {
            redemptionRepository.deleteByVoucherIdAndBookingId(
                    booking.getVoucherId(), booking.getId()
            );

            Voucher voucher = voucherRepository.findById(booking.getVoucherId()).orElse(null);
            if (voucher != null) {
                voucher.setRedeemedCount(Math.max(0, voucher.getRedeemedCount() - 1));
                if (voucher.getStatus() == VoucherStatus.USED_UP) {
                    voucher.setStatus(VoucherStatus.ACTIVE);
                }
                voucherRepository.save(voucher);
            }
        }
    }
}
