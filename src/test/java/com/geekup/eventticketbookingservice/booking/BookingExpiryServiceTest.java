package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.catalog.TicketInventoryRepository;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherRedemptionRepository;
import com.geekup.eventticketbookingservice.voucher.VoucherRepository;
import com.geekup.eventticketbookingservice.voucher.VoucherStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingExpiryServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingItemRepository bookingItemRepository;

    @Mock
    private TicketInventoryRepository inventoryRepository;

    @Mock
    private VoucherRedemptionRepository redemptionRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private InventoryRedisService inventoryRedisService;

    @InjectMocks
    private BookingExpiryService bookingExpiryService;

    @Test
    @DisplayName("expireBooking restores inventory, deletes redemption, restores USED_UP voucher to ACTIVE")
    void expireBooking_WithUsedUpVoucher_RestoresVoucherAndInventory() {
        Booking booking = Booking.builder()
                .id(100L)
                .voucherId(200L)
                .status(BookingStatus.RECEIVED)
                .build();

        BookingItem item = BookingItem.builder()
                .id(1L)
                .bookingId(100L)
                .ticketCategoryId(10L)
                .quantity(2)
                .build();

        TicketInventory inventory = TicketInventory.builder()
                .ticketCategoryId(10L)
                .totalQuantity(100)
                .reservedQuantity(2)
                .soldQuantity(0)
                .build();

        Voucher voucher = Voucher.builder()
                .id(200L)
                .redeemedCount(1)
                .status(VoucherStatus.USED_UP)
                .build();

        when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of(item));
        when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(voucherRepository.findById(200L)).thenReturn(Optional.of(voucher));

        bookingExpiryService.expireBooking(booking);

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(bookingRepository).save(booking);

        // Verify inventory released in DB & Redis
        assertEquals(0, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
        verify(inventoryRedisService).release(10L, 2);

        // Verify voucher redemption record deleted and count decremented & status restored to ACTIVE
        verify(redemptionRepository).deleteByVoucherIdAndBookingId(200L, 100L);
        assertEquals(0, voucher.getRedeemedCount());
        assertEquals(VoucherStatus.ACTIVE, voucher.getStatus());
        verify(voucherRepository).save(voucher);
    }

    @Test
    @DisplayName("expireBooking with ACTIVE voucher decrements count and keeps status ACTIVE")
    void expireBooking_WithActiveVoucher_DecrementsCountKeepsActive() {
        Booking booking = Booking.builder()
                .id(100L)
                .voucherId(200L)
                .status(BookingStatus.RECEIVED)
                .build();

        BookingItem item = BookingItem.builder()
                .id(1L)
                .bookingId(100L)
                .ticketCategoryId(10L)
                .quantity(1)
                .build();

        TicketInventory inventory = TicketInventory.builder()
                .ticketCategoryId(10L)
                .totalQuantity(50)
                .reservedQuantity(1)
                .build();

        Voucher voucher = Voucher.builder()
                .id(200L)
                .redeemedCount(3)
                .status(VoucherStatus.ACTIVE)
                .build();

        when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of(item));
        when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(voucherRepository.findById(200L)).thenReturn(Optional.of(voucher));

        bookingExpiryService.expireBooking(booking);

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(redemptionRepository).deleteByVoucherIdAndBookingId(200L, 100L);
        assertEquals(2, voucher.getRedeemedCount());
        assertEquals(VoucherStatus.ACTIVE, voucher.getStatus());
        verify(voucherRepository).save(voucher);
    }

    @Test
    @DisplayName("expireBooking without voucher only releases inventory and does not touch voucher repo")
    void expireBooking_WithoutVoucher_OnlyReleasesInventory() {
        Booking booking = Booking.builder()
                .id(100L)
                .voucherId(null)
                .status(BookingStatus.PENDING_PAYMENT)
                .build();

        BookingItem item1 = BookingItem.builder()
                .id(1L)
                .bookingId(100L)
                .ticketCategoryId(10L)
                .quantity(2)
                .build();

        BookingItem item2 = BookingItem.builder()
                .id(2L)
                .bookingId(100L)
                .ticketCategoryId(20L)
                .quantity(3)
                .build();

        TicketInventory inv1 = TicketInventory.builder().ticketCategoryId(10L).reservedQuantity(5).build();
        TicketInventory inv2 = TicketInventory.builder().ticketCategoryId(20L).reservedQuantity(3).build();

        when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of(item1, item2));
        when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(inv2));

        bookingExpiryService.expireBooking(booking);

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(bookingRepository).save(booking);

        assertEquals(3, inv1.getReservedQuantity());
        assertEquals(0, inv2.getReservedQuantity());
        verify(inventoryRepository).save(inv1);
        verify(inventoryRepository).save(inv2);

        verify(inventoryRedisService).release(10L, 2);
        verify(inventoryRedisService).release(20L, 3);

        verifyNoInteractions(redemptionRepository);
        verifyNoInteractions(voucherRepository);
    }

    @Test
    @DisplayName("expireBooking handles case where voucher is not found in database gracefully")
    void expireBooking_VoucherNotFoundInDb_HandlesNullSafely() {
        Booking booking = Booking.builder()
                .id(100L)
                .voucherId(999L)
                .status(BookingStatus.RECEIVED)
                .build();

        when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of());
        when(voucherRepository.findById(999L)).thenReturn(Optional.empty());

        bookingExpiryService.expireBooking(booking);

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(redemptionRepository).deleteByVoucherIdAndBookingId(999L, 100L);
        verify(voucherRepository, never()).save(any());
    }
}
