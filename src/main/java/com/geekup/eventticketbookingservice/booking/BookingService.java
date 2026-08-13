package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.booking.dto.BookingResponse;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.catalog.Concert;
import com.geekup.eventticketbookingservice.catalog.ConcertRepository;
import com.geekup.eventticketbookingservice.catalog.TicketCategory;
import com.geekup.eventticketbookingservice.catalog.TicketCategoryRepository;
import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.catalog.TicketInventoryRepository;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    
    private final TicketCategoryRepository categoryRepository;
    private final TicketInventoryRepository inventoryRepository;
    private final ConcertRepository concertRepository;
    private final VoucherService voucherService;
    private final BookingMapper bookingMapper;

    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest request, String idempotencyKey) {
        // 1. Idempotency Check
        var existing = bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return bookingMapper.toBookingResponse(existing.get(), bookingItemRepository.findByBookingId(existing.get().getId()));
        }

        // 2. Validate Items & Lock Inventory
        BigDecimal subtotal = BigDecimal.ZERO;
        List<BookingItem> bookingItems = new ArrayList<>();
        
        for (var itemReq : request.getItems()) {
            TicketCategory category = categoryRepository.findById(itemReq.getTicketCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.TICKET_CATEGORY_NOT_FOUND));

            // Validate sale window
            Concert concert = concertRepository.findById(category.getConcertId()).orElseThrow();
            ZonedDateTime now = ZonedDateTime.now();
            if (now.isBefore(concert.getSaleStartAt()) || now.isAfter(concert.getSaleEndAt())) {
                throw new AppException(ErrorCode.CONCERT_NOT_FOUND, "Concert is not in sale period");
            }

            if (itemReq.getQuantity() > category.getMaxPerBooking()) {
                throw new AppException(ErrorCode.NOT_ENOUGH_TICKETS, "Exceeds max per booking limit");
            }

            // Lock inventory
            TicketInventory inventory = inventoryRepository.findByIdForUpdate(category.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.TICKET_CATEGORY_NOT_FOUND, "Inventory not found"));

            int available = inventory.getTotalQuantity() - inventory.getReservedQuantity() - inventory.getSoldQuantity();
            if (available < itemReq.getQuantity()) {
                throw new AppException(ErrorCode.TICKET_SOLD_OUT, "Not enough tickets for category " + category.getName());
            }

            // Deduct
            inventory.setReservedQuantity(inventory.getReservedQuantity() + itemReq.getQuantity());
            inventoryRepository.save(inventory);

            BigDecimal itemSubtotal = category.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            bookingItems.add(BookingItem.builder()
                    .ticketCategoryId(category.getId())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(category.getPrice())
                    .build());
        }

        // 3. Voucher (Lock Voucher)
        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            voucher = voucherService.validateAndLock(request.getVoucherCode(), userId);
            discount = voucherService.calculateDiscount(voucher, subtotal);
        }

        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        // 4. Create Booking
        Booking booking = Booking.builder()
                .bookingCode("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(userId)
                .status(BookingStatus.RECEIVED)
                .subtotal(subtotal)
                .discountAmount(discount)
                .totalAmount(total)
                .voucherId(voucher != null ? voucher.getId() : null)
                .expiresAt(ZonedDateTime.now().plusMinutes(15)) // 15 mins to pay
                .idempotencyKey(idempotencyKey)
                .build();
        
        booking = bookingRepository.save(booking);

        // Save Items
        for (BookingItem item : bookingItems) {
            item.setBookingId(booking.getId());
            bookingItemRepository.save(item);
        }


        // Redeem Voucher
        if (voucher != null) {
            voucherService.applyRedemption(voucher, userId, booking.getId(), discount);
        }

        return bookingMapper.toBookingResponse(booking, bookingItems);
    }

    @Transactional
    public BookingResponse confirmPayment(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (booking.getStatus() != BookingStatus.RECEIVED && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATUS, "Cannot pay for booking in status " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.PAID);
        bookingRepository.save(booking);


        // Move reserved to sold
        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        for (BookingItem item : items) {
            TicketInventory inventory = inventoryRepository.findByIdForUpdate(item.getTicketCategoryId()).orElseThrow();
            inventory.setReservedQuantity(inventory.getReservedQuantity() - item.getQuantity());
            inventory.setSoldQuantity(inventory.getSoldQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);
        }

        return bookingMapper.toBookingResponse(booking, items);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(b -> bookingMapper.toBookingResponse(b, bookingItemRepository.findByBookingId(b.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
                
        if (!booking.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        
        return bookingMapper.toBookingResponse(booking, bookingItemRepository.findByBookingId(booking.getId()));
    }

}
