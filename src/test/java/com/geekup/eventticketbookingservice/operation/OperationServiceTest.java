package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.booking.*;
import com.geekup.eventticketbookingservice.catalog.*;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import com.geekup.eventticketbookingservice.operation.dto.CreateConcertRequest;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingStatusRequest;
import com.geekup.eventticketbookingservice.voucher.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationService Unit Tests")
class OperationServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private ConcertMapper concertMapper;

    @Mock
    private TicketCategoryRepository categoryRepository;

    @Mock
    private TicketInventoryRepository inventoryRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingItemRepository bookingItemRepository;

    @Mock
    private ConcertService concertService;

    @Mock
    private TicketCategoryService ticketCategoryService;

    @Mock
    private InventoryRedisService inventoryRedisService;

    @InjectMocks
    private OperationService operationService;

    private Concert testConcert;
    private TicketCategory testCategory;
    private TicketInventory testInventory;
    private Voucher testVoucher;
    private Booking testBooking;
    private BookingItem testBookingItem;

    @BeforeEach
    void setUp() {
        testConcert = Concert.builder()
                .id(1L)
                .name("Rock Festival")
                .description("Annual rock fest")
                .venue("Stadium A")
                .startAt(ZonedDateTime.now().plusDays(10))
                .endAt(ZonedDateTime.now().plusDays(10).plusHours(4))
                .saleStartAt(ZonedDateTime.now().minusDays(1))
                .saleEndAt(ZonedDateTime.now().plusDays(9))
                .status(ConcertStatus.DRAFT)
                .build();

        testCategory = TicketCategory.builder()
                .id(10L)
                .concertId(1L)
                .name("VIP")
                .price(new BigDecimal("150.00"))
                .maxPerBooking(4)
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        testInventory = TicketInventory.builder()
                .ticketCategoryId(10L)
                .totalQuantity(100)
                .reservedQuantity(10)
                .soldQuantity(20)
                .build();

        testVoucher = Voucher.builder()
                .id(100L)
                .name("Summer Promo")
                .code("SUMMER2026")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15.00"))
                .maxRedemptions(50)
                .redeemedCount(50)
                .maxPerUser(1)
                .startsAt(ZonedDateTime.now().minusDays(1))
                .endsAt(ZonedDateTime.now().plusDays(10))
                .status(VoucherStatus.USED_UP)
                .build();

        testBooking = Booking.builder()
                .id(1000L)
                .bookingCode("BK-1000")
                .userId(5L)
                .voucherId(100L)
                .status(BookingStatus.RECEIVED)
                .subtotal(new BigDecimal("300.00"))
                .discountAmount(new BigDecimal("45.00"))
                .totalAmount(new BigDecimal("255.00"))
                .build();

        testBookingItem = BookingItem.builder()
                .id(501L)
                .bookingId(1000L)
                .ticketCategoryId(10L)
                .quantity(2)
                .unitPrice(new BigDecimal("150.00"))
                .build();
    }

    @Nested
    @DisplayName("Concert Operations")
    class ConcertTests {

        @Test
        @DisplayName("createConcert maps request and saves concert entity")
        void createConcert_Success() {
            CreateConcertRequest request = new CreateConcertRequest();
            request.setName("Rock Festival");
            request.setVenue("Stadium A");

            when(concertMapper.toConcert(request)).thenReturn(testConcert);
            when(concertRepository.save(testConcert)).thenReturn(testConcert);

            Concert result = operationService.createConcert(request);

            assertNotNull(result);
            assertEquals("Rock Festival", result.getName());
            verify(concertMapper, times(1)).toConcert(request);
            verify(concertRepository, times(1)).save(testConcert);
        }

        @Test
        @DisplayName("publishConcert sets status to PUBLISHED and evicts concert cache")
        void publishConcert_Success() {
            when(concertRepository.findById(1L)).thenReturn(Optional.of(testConcert));
            when(concertRepository.save(any(Concert.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Concert result = operationService.publishConcert(1L);

            assertNotNull(result);
            assertEquals(ConcertStatus.PUBLISHED, result.getStatus());
            verify(concertRepository, times(1)).save(testConcert);
            verify(concertService, times(1)).evictConcertCache();
        }

        @Test
        @DisplayName("publishConcert throws NoSuchElementException when concert not found")
        void publishConcert_NotFound_ThrowsException() {
            when(concertRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> operationService.publishConcert(999L));
            verify(concertRepository, never()).save(any());
            verify(concertService, never()).evictConcertCache();
        }
    }

    @Nested
    @DisplayName("Ticket Category & Inventory Operations")
    class CategoryAndInventoryTests {

        @Test
        @DisplayName("addTicketCategory links to concert, sets ACTIVE status, saves, and evicts cache")
        void addTicketCategory_Success() {
            TicketCategory newCategory = TicketCategory.builder()
                    .name("General Admission")
                    .price(new BigDecimal("80.00"))
                    .maxPerBooking(6)
                    .build();

            when(concertRepository.findById(1L)).thenReturn(Optional.of(testConcert));
            when(categoryRepository.save(any(TicketCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketCategory result = operationService.addTicketCategory(1L, newCategory);

            assertNotNull(result);
            assertEquals(1L, result.getConcertId());
            assertEquals(TicketCategoryStatus.ACTIVE, result.getStatus());
            verify(categoryRepository, times(1)).save(newCategory);
            verify(ticketCategoryService, times(1)).evictCategoryCache(1L);
        }

        @Test
        @DisplayName("addTicketCategory throws NoSuchElementException when concert not found")
        void addTicketCategory_ConcertNotFound_ThrowsException() {
            TicketCategory newCategory = TicketCategory.builder().name("VIP").build();
            when(concertRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> operationService.addTicketCategory(999L, newCategory));
            verify(categoryRepository, never()).save(any());
            verify(ticketCategoryService, never()).evictCategoryCache(anyLong());
        }

        @Test
        @DisplayName("setInventory updates existing inventory, pre-warms redis with available tickets, and evicts category cache")
        void setInventory_ExistingInventory_UpdatesAndPrewarms() {
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(testCategory));
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(testInventory));
            when(inventoryRepository.save(any(TicketInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // totalQuantity = 100, reserved = 10, sold = 20 -> available = 70
            TicketInventory result = operationService.setInventory(10L, 100);

            assertNotNull(result);
            assertEquals(100, result.getTotalQuantity());
            verify(inventoryRepository, times(1)).save(testInventory);
            verify(inventoryRedisService, times(1)).preWarm(10L, 70);
            verify(ticketCategoryService, times(1)).evictCategoryCache(testCategory.getConcertId());
        }

        @Test
        @DisplayName("setInventory creates new inventory entity if not existing")
        void setInventory_NewInventory_CreatesAndPrewarms() {
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(testCategory));
            when(inventoryRepository.findById(10L)).thenReturn(Optional.empty());
            when(inventoryRepository.save(any(TicketInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketInventory result = operationService.setInventory(10L, 200);

            assertNotNull(result);
            assertEquals(200, result.getTotalQuantity());
            assertEquals(0, result.getReservedQuantity());
            assertEquals(0, result.getSoldQuantity());
            verify(inventoryRepository, times(1)).save(any(TicketInventory.class));
            verify(inventoryRedisService, times(1)).preWarm(10L, 200);
            verify(ticketCategoryService, times(1)).evictCategoryCache(testCategory.getConcertId());
        }

        @Test
        @DisplayName("setInventory clamps available quantity to 0 when totalQuantity is less than reserved + sold")
        void setInventory_Overbooked_ClampsAvailableToZero() {
            // reserved = 10, sold = 20 (sum = 30), total = 25 -> available = max(0, 25 - 30) = 0
            when(categoryRepository.findById(10L)).thenReturn(Optional.of(testCategory));
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(testInventory));
            when(inventoryRepository.save(any(TicketInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TicketInventory result = operationService.setInventory(10L, 25);

            assertNotNull(result);
            assertEquals(25, result.getTotalQuantity());
            verify(inventoryRedisService, times(1)).preWarm(10L, 0);
        }

        @Test
        @DisplayName("setInventory throws NoSuchElementException when category not found")
        void setInventory_CategoryNotFound_ThrowsException() {
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> operationService.setInventory(999L, 100));
            verify(inventoryRepository, never()).save(any());
            verify(inventoryRedisService, never()).preWarm(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("Voucher Operations")
    class VoucherTests {

        @Test
        @DisplayName("createVoucher with explicit code creates and saves active voucher")
        void createVoucher_WithExplicitCode_Success() {
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Voucher voucher = operationService.createVoucher(
                    "Black Friday",
                    "BF2026",
                    DiscountType.PERCENTAGE,
                    new BigDecimal("20.00"),
                    100,
                    1,
                    ZonedDateTime.now(),
                    ZonedDateTime.now().plusDays(5)
            );

            assertNotNull(voucher);
            assertEquals("Black Friday", voucher.getName());
            assertEquals("BF2026", voucher.getCode());
            assertEquals(VoucherStatus.ACTIVE, voucher.getStatus());
            assertEquals(0, voucher.getRedeemedCount());
            assertEquals(100, voucher.getMaxRedemptions());
            verify(voucherRepository, times(1)).save(any(Voucher.class));
        }

        @Test
        @DisplayName("createVoucher with null code generates auto code prefixed with FLASH-")
        void createVoucher_WithNullCode_GeneratesAutoCode() {
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Voucher voucher = operationService.createVoucher(
                    "Flash Sale",
                    null,
                    DiscountType.FIXED,
                    new BigDecimal("50.00"),
                    20,
                    1,
                    ZonedDateTime.now(),
                    ZonedDateTime.now().plusHours(2)
            );

            assertNotNull(voucher);
            assertNotNull(voucher.getCode());
            assertTrue(voucher.getCode().startsWith("FLASH-"));
            assertEquals(VoucherStatus.ACTIVE, voucher.getStatus());
            verify(voucherRepository, times(1)).save(any(Voucher.class));
        }

        @Test
        @DisplayName("disableVoucher sets status to DISABLED")
        void disableVoucher_Success() {
            testVoucher.setStatus(VoucherStatus.ACTIVE);
            when(voucherRepository.findById(100L)).thenReturn(Optional.of(testVoucher));
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Voucher result = operationService.disableVoucher(100L);

            assertNotNull(result);
            assertEquals(VoucherStatus.DISABLED, result.getStatus());
            verify(voucherRepository, times(1)).save(testVoucher);
        }

        @Test
        @DisplayName("disableVoucher throws AppException when voucher not found")
        void disableVoucher_NotFound_ThrowsAppException() {
            when(voucherRepository.findById(999L)).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> operationService.disableVoucher(999L));
            assertEquals(ErrorCode.VOUCHER_NOT_FOUND, exception.getErrorCode());
            verify(voucherRepository, never()).save(any());
        }

        @Test
        @DisplayName("enableVoucher sets status to ACTIVE")
        void enableVoucher_Success() {
            testVoucher.setStatus(VoucherStatus.DISABLED);
            when(voucherRepository.findById(100L)).thenReturn(Optional.of(testVoucher));
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Voucher result = operationService.enableVoucher(100L);

            assertNotNull(result);
            assertEquals(VoucherStatus.ACTIVE, result.getStatus());
            verify(voucherRepository, times(1)).save(testVoucher);
        }

        @Test
        @DisplayName("enableVoucher throws AppException when voucher not found")
        void enableVoucher_NotFound_ThrowsAppException() {
            when(voucherRepository.findById(999L)).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class, () -> operationService.enableVoucher(999L));
            assertEquals(ErrorCode.VOUCHER_NOT_FOUND, exception.getErrorCode());
            verify(voucherRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Booking Operations")
    class BookingTests {

        @Test
        @DisplayName("getAllBookings returns all bookings from repository")
        void getAllBookings_Success() {
            when(bookingRepository.findAll()).thenReturn(List.of(testBooking));

            List<Booking> bookings = operationService.getAllBookings();

            assertNotNull(bookings);
            assertEquals(1, bookings.size());
            assertEquals(testBooking.getId(), bookings.getFirst().getId());
            verify(bookingRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("updateBookingStatus updates booking status and returns updated booking")
        void updateBookingStatus_Success() {
            UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
            request.setStatus(BookingStatus.PAID);
            request.setReason("Admin verified payment");

            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Booking updated = operationService.updateBookingStatus(1000L, request, 99L);

            assertNotNull(updated);
            assertEquals(BookingStatus.PAID, updated.getStatus());
            verify(bookingRepository, times(1)).save(testBooking);
        }

        @Test
        @DisplayName("updateBookingStatus throws NoSuchElementException when booking not found")
        void updateBookingStatus_NotFound_ThrowsException() {
            UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
            request.setStatus(BookingStatus.PAID);

            when(bookingRepository.findById(9999L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> operationService.updateBookingStatus(9999L, request, 99L));
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory does nothing if booking is already CANCELLED")
        void cancelBooking_AlreadyCancelled_NoOp() {
            testBooking.setStatus(BookingStatus.CANCELLED);
            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));

            operationService.cancelBookingAndReleaseInventory(1000L, 99L);

            verify(bookingRepository, never()).save(any());
            verify(bookingItemRepository, never()).findByBookingId(anyLong());
            verify(inventoryRepository, never()).findByIdForUpdate(anyLong());
            verify(inventoryRedisService, never()).release(anyLong(), anyInt());
            verify(voucherRedemptionRepository, never()).deleteByVoucherIdAndBookingId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory does nothing if booking is already EXPIRED")
        void cancelBooking_AlreadyExpired_NoOp() {
            testBooking.setStatus(BookingStatus.EXPIRED);
            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));

            operationService.cancelBookingAndReleaseInventory(1000L, 99L);

            verify(bookingRepository, never()).save(any());
            verify(bookingItemRepository, never()).findByBookingId(anyLong());
            verify(inventoryRepository, never()).findByIdForUpdate(anyLong());
            verify(inventoryRedisService, never()).release(anyLong(), anyInt());
            verify(voucherRedemptionRepository, never()).deleteByVoucherIdAndBookingId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory with RECEIVED status reduces reservedQuantity, releases Redis inventory, and restores voucher")
        void cancelBooking_StatusReceived_ReleasesReservedInventoryAndVoucher() {
            testBooking.setStatus(BookingStatus.RECEIVED);

            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));
            when(bookingItemRepository.findByBookingId(1000L)).thenReturn(List.of(testBookingItem));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testInventory));
            when(voucherRepository.findById(100L)).thenReturn(Optional.of(testVoucher));

            operationService.cancelBookingAndReleaseInventory(1000L, 99L);

            // Verify booking cancelled
            assertEquals(BookingStatus.CANCELLED, testBooking.getStatus());
            verify(bookingRepository, times(1)).save(testBooking);

            // Verify inventory reserved quantity reduced (10 - 2 = 8)
            assertEquals(8, testInventory.getReservedQuantity());
            verify(inventoryRepository, times(1)).save(testInventory);
            verify(inventoryRedisService, times(1)).release(10L, 2);

            // Verify voucher redemption deleted and voucher count decremented & status restored from USED_UP to ACTIVE
            verify(voucherRedemptionRepository, times(1)).deleteByVoucherIdAndBookingId(100L, 1000L);
            assertEquals(49, testVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, testVoucher.getStatus());
            verify(voucherRepository, times(1)).save(testVoucher);
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory with PENDING_PAYMENT status reduces reservedQuantity")
        void cancelBooking_StatusPendingPayment_ReleasesReservedInventory() {
            testBooking.setStatus(BookingStatus.PENDING_PAYMENT);
            testBooking.setVoucherId(null);

            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));
            when(bookingItemRepository.findByBookingId(1000L)).thenReturn(List.of(testBookingItem));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testInventory));

            operationService.cancelBookingAndReleaseInventory(1000L, 99L);

            assertEquals(BookingStatus.CANCELLED, testBooking.getStatus());
            assertEquals(8, testInventory.getReservedQuantity());
            verify(inventoryRepository, times(1)).save(testInventory);
            verify(inventoryRedisService, times(1)).release(10L, 2);
            verify(voucherRedemptionRepository, never()).deleteByVoucherIdAndBookingId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory with PAID status reduces soldQuantity")
        void cancelBooking_StatusPaid_ReleasesSoldQuantity() {
            testBooking.setStatus(BookingStatus.PAID);
            testBooking.setVoucherId(null);

            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));
            when(bookingItemRepository.findByBookingId(1000L)).thenReturn(List.of(testBookingItem));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testInventory));

            operationService.cancelBookingAndReleaseInventory(1000L, 99L);

            assertEquals(BookingStatus.CANCELLED, testBooking.getStatus());
            // soldQuantity was 20, item quantity is 2 -> new soldQuantity is 18
            assertEquals(18, testInventory.getSoldQuantity());
            verify(inventoryRepository, times(1)).save(testInventory);
            verify(inventoryRedisService, times(1)).release(10L, 2);
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory with ACTIVE voucher retains ACTIVE status when decremented")
        void cancelBooking_VoucherActive_RetainsActiveStatus() {
            testBooking.setStatus(BookingStatus.RECEIVED);
            testVoucher.setStatus(VoucherStatus.ACTIVE);
            testVoucher.setRedeemedCount(5);

            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));
            when(bookingItemRepository.findByBookingId(1000L)).thenReturn(List.of(testBookingItem));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testInventory));
            when(voucherRepository.findById(100L)).thenReturn(Optional.of(testVoucher));

            operationService.cancelBookingAndReleaseInventory(1000L, 99L);

            assertEquals(4, testVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, testVoucher.getStatus());
            verify(voucherRepository, times(1)).save(testVoucher);
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory handles null voucher in repository gracefully")
        void cancelBooking_VoucherNotFoundInRepo_HandledGracefully() {
            testBooking.setStatus(BookingStatus.RECEIVED);

            when(bookingRepository.findById(1000L)).thenReturn(Optional.of(testBooking));
            when(bookingItemRepository.findByBookingId(1000L)).thenReturn(List.of(testBookingItem));
            when(inventoryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testInventory));
            when(voucherRepository.findById(100L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> operationService.cancelBookingAndReleaseInventory(1000L, 99L));
            verify(voucherRedemptionRepository, times(1)).deleteByVoucherIdAndBookingId(100L, 1000L);
            verify(voucherRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelBookingAndReleaseInventory throws NoSuchElementException when booking not found")
        void cancelBooking_BookingNotFound_ThrowsException() {
            when(bookingRepository.findById(9999L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> operationService.cancelBookingAndReleaseInventory(9999L, 99L));
            verify(bookingRepository, never()).save(any());
        }
    }
}
