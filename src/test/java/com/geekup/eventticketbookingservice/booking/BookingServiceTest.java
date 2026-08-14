package com.geekup.eventticketbookingservice.booking;

import com.geekup.eventticketbookingservice.booking.dto.BookingItemRequest;
import com.geekup.eventticketbookingservice.booking.dto.BookingResponse;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.catalog.*;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import com.geekup.eventticketbookingservice.voucher.DiscountType;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherService;
import com.geekup.eventticketbookingservice.voucher.VoucherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingItemRepository bookingItemRepository;

    @Mock
    private TicketCategoryRepository categoryRepository;

    @Mock
    private TicketInventoryRepository inventoryRepository;

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private InventoryRedisService inventoryRedisService;

    @InjectMocks
    private BookingService bookingService;

    private final Long userId = 42L;
    private final String idempotencyKey = "idem-uuid-999";

    private Concert activeConcert;
    private TicketCategory categoryVip;
    private TicketInventory inventoryVip;
    private Booking existingBooking;
    private BookingResponse mappedResponse;

    @BeforeEach
    void setUp() {
        activeConcert = Concert.builder()
                .id(1L)
                .name("Coldplay World Tour")
                .saleStartAt(ZonedDateTime.now().minusDays(1))
                .saleEndAt(ZonedDateTime.now().plusDays(10))
                .status(ConcertStatus.PUBLISHED)
                .build();

        categoryVip = TicketCategory.builder()
                .id(10L)
                .concertId(1L)
                .name("VIP")
                .price(new BigDecimal("100.00"))
                .maxPerBooking(4)
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        inventoryVip = TicketInventory.builder()
                .ticketCategoryId(10L)
                .totalQuantity(50)
                .reservedQuantity(0)
                .soldQuantity(0)
                .build();

        existingBooking = Booking.builder()
                .id(100L)
                .bookingCode("BK-EXISTING")
                .userId(userId)
                .status(BookingStatus.RECEIVED)
                .subtotal(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("200.00"))
                .idempotencyKey(idempotencyKey)
                .build();

        mappedResponse = BookingResponse.builder()
                .id(100L)
                .bookingCode("BK-EXISTING")
                .userId(userId)
                .status(BookingStatus.RECEIVED)
                .totalAmount(new BigDecimal("200.00"))
                .build();
    }

    private BookingItemRequest createItem(Long categoryId, int quantity) {
        BookingItemRequest req = new BookingItemRequest();
        req.setTicketCategoryId(categoryId);
        req.setQuantity(quantity);
        return req;
    }

    private CreateBookingRequest createBookingRequest(List<BookingItemRequest> items, String voucherCode) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setItems(items);
        req.setVoucherCode(voucherCode);
        return req;
    }

    @Nested
    @DisplayName("createBooking Tests")
    class CreateBookingTests {

        @Test
        @DisplayName("Idempotent request returns existing booking without re-deducting inventory")
        void createBooking_IdempotentHit_ReturnsExistingBooking() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                    .thenReturn(Optional.of(existingBooking));
            when(bookingItemRepository.findByBookingId(100L))
                    .thenReturn(List.of(BookingItem.builder().id(1L).bookingId(100L).ticketCategoryId(10L).quantity(2).build()));
            when(bookingMapper.toBookingResponse(eq(existingBooking), anyList()))
                    .thenReturn(mappedResponse);

            BookingResponse response = bookingService.createBooking(userId, request, idempotencyKey);

            assertNotNull(response);
            assertEquals(100L, response.getId());
            verify(bookingRepository, times(1)).findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(inventoryRedisService);
            verifyNoInteractions(inventoryRepository);
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("createBooking succeeds for valid request without voucher")
        void createBooking_Success_WithoutVoucher() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                b.setId(200L);
                return b;
            });
            when(bookingMapper.toBookingResponse(any(Booking.class), anyList())).thenReturn(mappedResponse);

            BookingResponse response = bookingService.createBooking(userId, request, idempotencyKey);

            assertNotNull(response);
            assertEquals(2, inventoryVip.getReservedQuantity());
            verify(inventoryRepository, times(1)).save(inventoryVip);

            ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository, times(1)).save(bookingCaptor.capture());
            Booking savedBooking = bookingCaptor.getValue();
            assertEquals(userId, savedBooking.getUserId());
            assertEquals(BookingStatus.RECEIVED, savedBooking.getStatus());
            assertEquals(new BigDecimal("200.00"), savedBooking.getSubtotal());
            assertEquals(BigDecimal.ZERO, savedBooking.getDiscountAmount());
            assertEquals(new BigDecimal("200.00"), savedBooking.getTotalAmount());
            assertNull(savedBooking.getVoucherId());

            verify(bookingItemRepository, times(1)).save(any(BookingItem.class));
            verifyNoInteractions(voucherService);
        }

        @Test
        @DisplayName("createBooking succeeds for valid request with voucher")
        void createBooking_Success_WithVoucher() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), "PROMO20");

            Voucher voucher = Voucher.builder()
                    .id(5L)
                    .code("PROMO20")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("20"))
                    .status(VoucherStatus.ACTIVE)
                    .build();

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));
            when(voucherService.validateAndLock("PROMO20", userId)).thenReturn(voucher);
            when(voucherService.calculateDiscount(voucher, new BigDecimal("200.00"))).thenReturn(new BigDecimal("40.00"));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                b.setId(300L);
                return b;
            });
            when(bookingMapper.toBookingResponse(any(Booking.class), anyList())).thenReturn(mappedResponse);

            BookingResponse response = bookingService.createBooking(userId, request, idempotencyKey);

            assertNotNull(response);
            ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository, times(1)).save(bookingCaptor.capture());
            Booking savedBooking = bookingCaptor.getValue();
            assertEquals(new BigDecimal("200.00"), savedBooking.getSubtotal());
            assertEquals(new BigDecimal("40.00"), savedBooking.getDiscountAmount());
            assertEquals(new BigDecimal("160.00"), savedBooking.getTotalAmount());
            assertEquals(5L, savedBooking.getVoucherId());

            verify(voucherService, times(1)).applyRedemption(voucher, userId, 300L, new BigDecimal("40.00"));
        }

        @Test
        @DisplayName("createBooking throws TICKET_CATEGORY_NOT_FOUND when category does not exist")
        void createBooking_CategoryNotFound_ThrowsException() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(999L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.TICKET_CATEGORY_NOT_FOUND, ex.getErrorCode());
            verify(inventoryRedisService, never()).tryDecrement(any(), anyInt());
        }

        @Test
        @DisplayName("createBooking throws CONCERT_NOT_FOUND when concert is before sale start period")
        void createBooking_BeforeSalePeriod_ThrowsException() {
            activeConcert.setSaleStartAt(ZonedDateTime.now().plusDays(2));
            activeConcert.setSaleEndAt(ZonedDateTime.now().plusDays(10));

            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.CONCERT_NOT_FOUND, ex.getErrorCode());
            assertEquals("Concert is not in sale period", ex.getMessage());
            verify(inventoryRedisService, never()).tryDecrement(any(), anyInt());
        }

        @Test
        @DisplayName("createBooking throws CONCERT_NOT_FOUND when concert is after sale end period")
        void createBooking_AfterSalePeriod_ThrowsException() {
            activeConcert.setSaleStartAt(ZonedDateTime.now().minusDays(10));
            activeConcert.setSaleEndAt(ZonedDateTime.now().minusDays(1));

            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.CONCERT_NOT_FOUND, ex.getErrorCode());
            assertEquals("Concert is not in sale period", ex.getMessage());
            verify(inventoryRedisService, never()).tryDecrement(any(), anyInt());
        }

        @Test
        @DisplayName("createBooking throws NOT_ENOUGH_TICKETS when quantity exceeds category maxPerBooking")
        void createBooking_ExceedsMaxPerBooking_ThrowsException() {
            categoryVip.setMaxPerBooking(2);

            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 5)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.NOT_ENOUGH_TICKETS, ex.getErrorCode());
            assertEquals("Exceeds max per booking limit", ex.getMessage());
            verify(inventoryRedisService, never()).tryDecrement(any(), anyInt());
        }

        @Test
        @DisplayName("createBooking throws TICKET_SOLD_OUT and releases previous items when Redis pre-filter fails")
        void createBooking_RedisTryDecrementFails_RollsBackAndThrows() {
            TicketCategory categoryStandard = TicketCategory.builder()
                    .id(20L)
                    .concertId(1L)
                    .name("Standard")
                    .price(new BigDecimal("50.00"))
                    .maxPerBooking(4)
                    .build();

            CreateBookingRequest request = createBookingRequest(List.of(
                    createItem(10L, 2),
                    createItem(20L, 2)
            ), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(categoryRepository.findById(20L)).thenReturn(Optional.of(categoryStandard));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));

            // Item 1 succeeds in Redis and DB
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));

            // Item 2 fails in Redis
            when(inventoryRedisService.tryDecrement(20L, 2)).thenReturn(false);

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.TICKET_SOLD_OUT, ex.getErrorCode());
            // Verify item 1 was rolled back from Redis
            verify(inventoryRedisService, times(1)).release(10L, 2);
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("createBooking throws TICKET_CATEGORY_NOT_FOUND when DB inventory not found and rolls back Redis")
        void createBooking_DbInventoryNotFound_RollsBackRedisAndThrows() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.TICKET_CATEGORY_NOT_FOUND, ex.getErrorCode());
            assertEquals("Inventory not found", ex.getMessage());
            verify(inventoryRedisService, times(1)).release(10L, 2);
        }

        @Test
        @DisplayName("createBooking throws TICKET_SOLD_OUT when DB inventory available < quantity and rolls back Redis")
        void createBooking_DbInventoryInsufficient_RollsBackRedisAndThrows() {
            inventoryVip.setTotalQuantity(10);
            inventoryVip.setReservedQuantity(8);
            inventoryVip.setSoldQuantity(1); // available = 1, requested = 2

            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(Optional.empty());
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.TICKET_SOLD_OUT, ex.getErrorCode());
            verify(inventoryRedisService, times(1)).release(10L, 2);
        }

        @Test
        @DisplayName("createBooking handles concurrent DataIntegrityViolationException and returns existing booking")
        void createBooking_ConcurrentConflict_ReturnsExisting() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                    .thenReturn(Optional.empty()) // first check
                    .thenReturn(Optional.of(existingBooking)); // retry check

            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));
            when(bookingRepository.save(any(Booking.class))).thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
            when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of());
            when(bookingMapper.toBookingResponse(eq(existingBooking), anyList())).thenReturn(mappedResponse);

            BookingResponse response = bookingService.createBooking(userId, request, idempotencyKey);

            assertNotNull(response);
            assertEquals(100L, response.getId());
        }

        @Test
        @DisplayName("createBooking throws INTERNAL_SERVER_ERROR if DataIntegrityViolationException occurs and retry find is empty")
        void createBooking_ConcurrentConflict_RetryFails_ThrowsInternalError() {
            CreateBookingRequest request = createBookingRequest(List.of(createItem(10L, 2)), null);

            when(bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty());

            when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryVip));
            when(concertRepository.findById(1L)).thenReturn(Optional.of(activeConcert));
            when(inventoryRedisService.tryDecrement(10L, 2)).thenReturn(true);
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));
            when(bookingRepository.save(any(Booking.class))).thenThrow(new DataIntegrityViolationException("unknown db error"));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.createBooking(userId, request, idempotencyKey));

            assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ex.getErrorCode());
            verify(inventoryRedisService, times(1)).release(10L, 2);
        }
    }

    @Nested
    @DisplayName("confirmPayment Tests")
    class ConfirmPaymentTests {

        @Test
        @DisplayName("confirmPayment succeeds when booking status is RECEIVED")
        void confirmPayment_Success_ReceivedStatus() {
            existingBooking.setStatus(BookingStatus.RECEIVED);
            BookingItem item = BookingItem.builder().id(1L).bookingId(100L).ticketCategoryId(10L).quantity(2).build();

            inventoryVip.setReservedQuantity(2);
            inventoryVip.setSoldQuantity(0);

            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));
            when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of(item));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));
            when(bookingMapper.toBookingResponse(eq(existingBooking), anyList())).thenReturn(mappedResponse);

            BookingResponse response = bookingService.confirmPayment(100L, userId);

            assertNotNull(response);
            assertEquals(BookingStatus.PAID, existingBooking.getStatus());
            assertEquals(0, inventoryVip.getReservedQuantity());
            assertEquals(2, inventoryVip.getSoldQuantity());
            verify(bookingRepository, times(1)).save(existingBooking);
            verify(inventoryRepository, times(1)).save(inventoryVip);
        }

        @Test
        @DisplayName("confirmPayment succeeds when booking status is PENDING_PAYMENT")
        void confirmPayment_Success_PendingPaymentStatus() {
            existingBooking.setStatus(BookingStatus.PENDING_PAYMENT);
            BookingItem item = BookingItem.builder().id(1L).bookingId(100L).ticketCategoryId(10L).quantity(1).build();

            inventoryVip.setReservedQuantity(3);
            inventoryVip.setSoldQuantity(2);

            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));
            when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of(item));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(inventoryVip));
            when(bookingMapper.toBookingResponse(eq(existingBooking), anyList())).thenReturn(mappedResponse);

            BookingResponse response = bookingService.confirmPayment(100L, userId);

            assertNotNull(response);
            assertEquals(BookingStatus.PAID, existingBooking.getStatus());
            assertEquals(2, inventoryVip.getReservedQuantity());
            assertEquals(3, inventoryVip.getSoldQuantity());
        }

        @Test
        @DisplayName("confirmPayment throws BOOKING_NOT_FOUND when booking does not exist")
        void confirmPayment_BookingNotFound_ThrowsException() {
            when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.confirmPayment(999L, userId));

            assertEquals(ErrorCode.BOOKING_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("confirmPayment throws INVALID_BOOKING_STATUS when booking is already PAID")
        void confirmPayment_AlreadyPaid_ThrowsException() {
            existingBooking.setStatus(BookingStatus.PAID);
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.confirmPayment(100L, userId));

            assertEquals(ErrorCode.INVALID_BOOKING_STATUS, ex.getErrorCode());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("confirmPayment throws INVALID_BOOKING_STATUS when booking is CANCELLED")
        void confirmPayment_Cancelled_ThrowsException() {
            existingBooking.setStatus(BookingStatus.CANCELLED);
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.confirmPayment(100L, userId));

            assertEquals(ErrorCode.INVALID_BOOKING_STATUS, ex.getErrorCode());
        }

        @Test
        @DisplayName("confirmPayment throws INVALID_BOOKING_STATUS when booking is EXPIRED")
        void confirmPayment_Expired_ThrowsException() {
            existingBooking.setStatus(BookingStatus.EXPIRED);
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.confirmPayment(100L, userId));

            assertEquals(ErrorCode.INVALID_BOOKING_STATUS, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getUserBookings Tests")
    class GetUserBookingsTests {

        @Test
        @DisplayName("getUserBookings returns mapped responses for user")
        void getUserBookings_ReturnsList() {
            when(bookingRepository.findByUserId(userId)).thenReturn(List.of(existingBooking));
            when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of());
            when(bookingMapper.toBookingResponse(eq(existingBooking), anyList())).thenReturn(mappedResponse);

            List<BookingResponse> responses = bookingService.getUserBookings(userId);

            assertNotNull(responses);
            assertEquals(1, responses.size());
            assertEquals(100L, responses.getFirst().getId());
            verify(bookingRepository, times(1)).findByUserId(userId);
        }

        @Test
        @DisplayName("getUserBookings returns empty list when user has no bookings")
        void getUserBookings_EmptyList() {
            when(bookingRepository.findByUserId(userId)).thenReturn(List.of());

            List<BookingResponse> responses = bookingService.getUserBookings(userId);

            assertNotNull(responses);
            assertTrue(responses.isEmpty());
        }
    }

    @Nested
    @DisplayName("getBooking Tests")
    class GetBookingTests {

        @Test
        @DisplayName("getBooking returns booking response when user is owner")
        void getBooking_Owner_ReturnsResponse() {
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));
            when(bookingItemRepository.findByBookingId(100L)).thenReturn(List.of());
            when(bookingMapper.toBookingResponse(eq(existingBooking), anyList())).thenReturn(mappedResponse);

            BookingResponse response = bookingService.getBooking(100L, userId);

            assertNotNull(response);
            assertEquals(100L, response.getId());
            verify(bookingRepository, times(1)).findById(100L);
        }

        @Test
        @DisplayName("getBooking throws BOOKING_NOT_FOUND when booking does not exist")
        void getBooking_NotFound_ThrowsException() {
            when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.getBooking(999L, userId));

            assertEquals(ErrorCode.BOOKING_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("getBooking throws FORBIDDEN when user is not the owner")
        void getBooking_NotOwner_ThrowsForbidden() {
            when(bookingRepository.findById(100L)).thenReturn(Optional.of(existingBooking));

            AppException ex = assertThrows(AppException.class,
                    () -> bookingService.getBooking(100L, 999L)); // user 999 is not user 42

            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }
}
