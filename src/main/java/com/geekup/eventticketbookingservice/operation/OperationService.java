package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.booking.*;
import com.geekup.eventticketbookingservice.catalog.*;
import com.geekup.eventticketbookingservice.operation.dto.CreateConcertRequest;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingStatusRequest;
import com.geekup.eventticketbookingservice.voucher.*;
import com.geekup.eventticketbookingservice.catalog.ConcertMapper;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationService {

    private final ConcertRepository concertRepository;
    private final ConcertMapper concertMapper;
    private final TicketCategoryRepository categoryRepository;
    private final TicketInventoryRepository inventoryRepository;
    
    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;
    
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;

    private final ConcertService concertService;
    private final TicketCategoryService ticketCategoryService;
    private final com.geekup.eventticketbookingservice.inventory.InventoryRedisService inventoryRedisService;

    // Concert
    @Transactional
    public Concert createConcert(CreateConcertRequest request) {
        Concert concert = concertMapper.toConcert(request);
        return concertRepository.save(concert);
    }

    @Transactional
    public Concert publishConcert(Long id) {
        Concert concert = concertRepository.findById(id).orElseThrow();
        concert.setStatus(ConcertStatus.PUBLISHED);
        Concert saved = concertRepository.save(concert);
        concertService.evictConcertCache();
        return saved;
    }

    @Transactional
    public TicketCategory addTicketCategory(Long concertId, TicketCategory category) {
        Concert concert = concertRepository.findById(concertId).orElseThrow();
        category.setConcertId(concert.getId());
        category.setStatus(TicketCategoryStatus.ACTIVE);
        TicketCategory saved = categoryRepository.save(category);
        ticketCategoryService.evictCategoryCache(concertId);
        return saved;
    }

    @Transactional
    public TicketInventory setInventory(Long categoryId, int totalQuantity) {
        TicketCategory category = categoryRepository.findById(categoryId).orElseThrow();
        
        TicketInventory inventory = inventoryRepository.findById(categoryId)
                .orElse(TicketInventory.builder()
                        .ticketCategoryId(categoryId)
                        .reservedQuantity(0)
                        .soldQuantity(0)
                        .build());
                        
        inventory.setTotalQuantity(totalQuantity);
        TicketInventory saved = inventoryRepository.save(inventory);

        // Pre-warm Redis with current available quantity
        int available = Math.max(0, totalQuantity - saved.getReservedQuantity() - saved.getSoldQuantity());
        inventoryRedisService.preWarm(categoryId, available);

        // Evict category cache so fresh quantity is fetched
        ticketCategoryService.evictCategoryCache(category.getConcertId());

        return saved;
    }

    @Transactional
    public Voucher createVoucher(String name, String code, DiscountType discountType, java.math.BigDecimal discountValue, int maxRedemptions, int maxPerUser, java.time.ZonedDateTime startsAt, java.time.ZonedDateTime endsAt) {
        Voucher voucher = Voucher.builder()
                .name(name)
                .code(code != null ? code : "FLASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .discountType(discountType)
                .discountValue(discountValue)
                .maxRedemptions(maxRedemptions)
                .redeemedCount(0)
                .maxPerUser(maxPerUser)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(VoucherStatus.ACTIVE)
                .build();
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher disableVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        voucher.setStatus(VoucherStatus.DISABLED);
        return voucherRepository.save(voucher);
    }

    @Transactional
    public Voucher enableVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        voucher.setStatus(VoucherStatus.ACTIVE);
        return voucherRepository.save(voucher);
    }

    // Booking
    @Transactional(readOnly = true)
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @Transactional
    public Booking updateBookingStatus(Long bookingId, UpdateBookingStatusRequest request, Long adminId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(request.getStatus());
        booking = bookingRepository.save(booking);


        return booking;
    }

    @Transactional
    public void cancelBookingAndReleaseInventory(Long bookingId, Long adminId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            return;
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Release inventory (DB and Redis)
        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        for (BookingItem item : items) {
            TicketInventory inventory = inventoryRepository.findByIdForUpdate(item.getTicketCategoryId()).orElseThrow();
            if (oldStatus == BookingStatus.RECEIVED || oldStatus == BookingStatus.PENDING_PAYMENT) {
                inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.getQuantity()));
            } else if (oldStatus == BookingStatus.PAID) {
                inventory.setSoldQuantity(Math.max(0, inventory.getSoldQuantity() - item.getQuantity()));
            }
            inventoryRepository.save(inventory);

            // Release Redis inventory
            inventoryRedisService.release(item.getTicketCategoryId(), item.getQuantity());
        }

        // Revert voucher redemption if any
        if (booking.getVoucherId() != null) {
            voucherRedemptionRepository.deleteByVoucherIdAndBookingId(booking.getVoucherId(), booking.getId());

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
