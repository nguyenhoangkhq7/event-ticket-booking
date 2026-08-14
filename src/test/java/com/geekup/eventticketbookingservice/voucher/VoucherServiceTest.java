package com.geekup.eventticketbookingservice.voucher;

import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @InjectMocks
    private VoucherService voucherService;

    private Voucher validVoucher;
    private final Long userId = 1L;
    private final Long bookingId = 100L;

    @BeforeEach
    void setUp() {
        validVoucher = Voucher.builder()
                .id(10L)
                .name("Summer Fest 2026")
                .code("SUMMER20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .maxRedemptions(100)
                .redeemedCount(10)
                .maxPerUser(1)
                .startsAt(ZonedDateTime.now().minusDays(1))
                .endsAt(ZonedDateTime.now().plusDays(5))
                .status(VoucherStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("validateAndLock Tests")
    class ValidateAndLockTests {

        @Test
        @DisplayName("validateAndLock succeeds when voucher is valid and user has not redeemed")
        void validateAndLock_Success() {
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));
            when(voucherRedemptionRepository.existsByVoucherIdAndUserId(10L, userId)).thenReturn(false);

            Voucher result = voucherService.validateAndLock("SUMMER20", userId);

            assertNotNull(result);
            assertEquals("SUMMER20", result.getCode());
            assertEquals(10L, result.getId());
            verify(voucherRepository, times(1)).findByCodeForUpdate("SUMMER20");
            verify(voucherRedemptionRepository, times(1)).existsByVoucherIdAndUserId(10L, userId);
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_NOT_FOUND when voucher code does not exist")
        void validateAndLock_NotFound_ThrowsException() {
            when(voucherRepository.findByCodeForUpdate("NONEXISTENT")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("NONEXISTENT", userId));

            assertEquals(ErrorCode.VOUCHER_NOT_FOUND, ex.getErrorCode());
            verify(voucherRedemptionRepository, never()).existsByVoucherIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when status is DISABLED")
        void validateAndLock_DisabledStatus_ThrowsException() {
            validVoucher.setStatus(VoucherStatus.DISABLED);
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
            assertEquals("Voucher is not active", ex.getMessage());
            verify(voucherRedemptionRepository, never()).existsByVoucherIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when status is USED_UP")
        void validateAndLock_UsedUpStatus_ThrowsException() {
            validVoucher.setStatus(VoucherStatus.USED_UP);
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
            assertEquals("Voucher is not active", ex.getMessage());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when status is EXPIRED")
        void validateAndLock_ExpiredStatus_ThrowsException() {
            validVoucher.setStatus(VoucherStatus.EXPIRED);
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
            assertEquals("Voucher is not active", ex.getMessage());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_LIMIT_REACHED when redeemedCount equals maxRedemptions")
        void validateAndLock_MaxRedemptionsReached_ThrowsException() {
            validVoucher.setRedeemedCount(100);
            validVoucher.setMaxRedemptions(100);
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_LIMIT_REACHED, ex.getErrorCode());
            verify(voucherRedemptionRepository, never()).existsByVoucherIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_LIMIT_REACHED when redeemedCount exceeds maxRedemptions")
        void validateAndLock_RedeemedCountExceedsMax_ThrowsException() {
            validVoucher.setRedeemedCount(105);
            validVoucher.setMaxRedemptions(100);
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_LIMIT_REACHED, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when current time is before startsAt")
        void validateAndLock_BeforeStartsAt_ThrowsException() {
            validVoucher.setStartsAt(ZonedDateTime.now().plusDays(2));
            validVoucher.setEndsAt(ZonedDateTime.now().plusDays(10));
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
            assertEquals("Voucher is expired or not yet active", ex.getMessage());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when current time is after endsAt")
        void validateAndLock_AfterEndsAt_ThrowsException() {
            validVoucher.setStartsAt(ZonedDateTime.now().minusDays(10));
            validVoucher.setEndsAt(ZonedDateTime.now().minusDays(1));
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
            assertEquals("Voucher is expired or not yet active", ex.getMessage());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_ALREADY_REDEEMED when user already used this voucher")
        void validateAndLock_AlreadyRedeemedByUser_ThrowsException() {
            when(voucherRepository.findByCodeForUpdate("SUMMER20")).thenReturn(Optional.of(validVoucher));
            when(voucherRedemptionRepository.existsByVoucherIdAndUserId(10L, userId)).thenReturn(true);

            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateAndLock("SUMMER20", userId));

            assertEquals(ErrorCode.VOUCHER_ALREADY_REDEEMED, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("calculateDiscount Tests")
    class CalculateDiscountTests {

        @Test
        @DisplayName("calculateDiscount for FIXED discount returns fixed amount when subtotal is larger")
        void calculateDiscount_FixedDiscount_SubtotalLarger() {
            Voucher fixedVoucher = Voucher.builder()
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("50.00"))
                    .build();

            BigDecimal subtotal = new BigDecimal("200.00");
            BigDecimal discount = voucherService.calculateDiscount(fixedVoucher, subtotal);

            assertEquals(0, new BigDecimal("50.00").compareTo(discount));
        }

        @Test
        @DisplayName("calculateDiscount for FIXED discount caps discount at subtotal when subtotal is smaller")
        void calculateDiscount_FixedDiscount_SubtotalSmaller() {
            Voucher fixedVoucher = Voucher.builder()
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("100.00"))
                    .build();

            BigDecimal subtotal = new BigDecimal("75.00");
            BigDecimal discount = voucherService.calculateDiscount(fixedVoucher, subtotal);

            assertEquals(0, new BigDecimal("75.00").compareTo(discount));
        }

        @Test
        @DisplayName("calculateDiscount for FIXED discount equals subtotal when both are equal")
        void calculateDiscount_FixedDiscount_SubtotalEqual() {
            Voucher fixedVoucher = Voucher.builder()
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("100.00"))
                    .build();

            BigDecimal subtotal = new BigDecimal("100.00");
            BigDecimal discount = voucherService.calculateDiscount(fixedVoucher, subtotal);

            assertEquals(0, new BigDecimal("100.00").compareTo(discount));
        }

        @Test
        @DisplayName("calculateDiscount for PERCENTAGE discount calculates correct proportional discount")
        void calculateDiscount_PercentageDiscount_Standard() {
            Voucher percentageVoucher = Voucher.builder()
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("20")) // 20%
                    .build();

            BigDecimal subtotal = new BigDecimal("250.00");
            BigDecimal discount = voucherService.calculateDiscount(percentageVoucher, subtotal);

            assertEquals(0, new BigDecimal("50.00").compareTo(discount));
        }

        @Test
        @DisplayName("calculateDiscount for PERCENTAGE discount 100% gives full subtotal")
        void calculateDiscount_PercentageDiscount_100Percent() {
            Voucher percentageVoucher = Voucher.builder()
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("100"))
                    .build();

            BigDecimal subtotal = new BigDecimal("150.00");
            BigDecimal discount = voucherService.calculateDiscount(percentageVoucher, subtotal);

            assertEquals(0, new BigDecimal("150.00").compareTo(discount));
        }

        @Test
        @DisplayName("calculateDiscount for PERCENTAGE discount 0% gives zero")
        void calculateDiscount_PercentageDiscount_ZeroPercent() {
            Voucher percentageVoucher = Voucher.builder()
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("0"))
                    .build();

            BigDecimal subtotal = new BigDecimal("150.00");
            BigDecimal discount = voucherService.calculateDiscount(percentageVoucher, subtotal);

            assertEquals(0, BigDecimal.ZERO.compareTo(discount));
        }
    }

    @Nested
    @DisplayName("applyRedemption Tests")
    class ApplyRedemptionTests {

        @Test
        @DisplayName("applyRedemption saves redemption record and increments redeemedCount while remaining ACTIVE")
        void applyRedemption_RemainingActive() {
            validVoucher.setMaxRedemptions(10);
            validVoucher.setRedeemedCount(5);
            validVoucher.setStatus(VoucherStatus.ACTIVE);

            BigDecimal discountAmount = new BigDecimal("30.00");

            voucherService.applyRedemption(validVoucher, userId, bookingId, discountAmount);

            // Verify redemption record saved
            ArgumentCaptor<VoucherRedemption> redemptionCaptor = ArgumentCaptor.forClass(VoucherRedemption.class);
            verify(voucherRedemptionRepository, times(1)).save(redemptionCaptor.capture());
            VoucherRedemption savedRedemption = redemptionCaptor.getValue();
            assertEquals(10L, savedRedemption.getVoucherId());
            assertEquals(userId, savedRedemption.getUserId());
            assertEquals(bookingId, savedRedemption.getBookingId());
            assertEquals(discountAmount, savedRedemption.getDiscountAmount());

            // Verify voucher count incremented and status unchanged
            ArgumentCaptor<Voucher> voucherCaptor = ArgumentCaptor.forClass(Voucher.class);
            verify(voucherRepository, times(1)).save(voucherCaptor.capture());
            Voucher savedVoucher = voucherCaptor.getValue();
            assertEquals(6, savedVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, savedVoucher.getStatus());
        }

        @Test
        @DisplayName("applyRedemption updates status to USED_UP when redeemedCount reaches maxRedemptions")
        void applyRedemption_ReachesMax_SetsUsedUp() {
            validVoucher.setMaxRedemptions(10);
            validVoucher.setRedeemedCount(9);
            validVoucher.setStatus(VoucherStatus.ACTIVE);

            BigDecimal discountAmount = new BigDecimal("20.00");

            voucherService.applyRedemption(validVoucher, userId, bookingId, discountAmount);

            assertEquals(10, validVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.USED_UP, validVoucher.getStatus());
            verify(voucherRepository, times(1)).save(validVoucher);
            verify(voucherRedemptionRepository, times(1)).save(any(VoucherRedemption.class));
        }
    }
}
