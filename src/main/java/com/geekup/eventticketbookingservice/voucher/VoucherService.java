package com.geekup.eventticketbookingservice.voucher;

import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;

    public Voucher validateAndLock(String code, Long userId) {
        Voucher voucher = voucherRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new AppException(ErrorCode.VOUCHER_INVALID, "Voucher is not active");
        }

        if (voucher.getRedeemedCount() >= voucher.getMaxRedemptions()) {
            throw new AppException(ErrorCode.VOUCHER_LIMIT_REACHED);
        }

        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(voucher.getStartsAt()) || now.isAfter(voucher.getEndsAt())) {
            throw new AppException(ErrorCode.VOUCHER_INVALID, "Voucher is expired or not yet active");
        }

        if (voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), userId)) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_REDEEMED);
        }

        return voucher;
    }

    public BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getDiscountType() == DiscountType.FIXED) {
            return voucher.getDiscountValue().min(subtotal);
        } else {
            // PERCENTAGE
            BigDecimal percentage = voucher.getDiscountValue().divide(new BigDecimal("100"));
            return subtotal.multiply(percentage);
        }
    }

    public void applyRedemption(Voucher voucher, Long userId, Long bookingId, BigDecimal discountAmount) {
        VoucherRedemption redemption = VoucherRedemption.builder()
                .voucherId(voucher.getId())
                .userId(userId)
                .bookingId(bookingId)
                .discountAmount(discountAmount)
                .build();
        
        voucherRedemptionRepository.save(redemption);
        
        voucher.setRedeemedCount(voucher.getRedeemedCount() + 1);
        if (voucher.getRedeemedCount().equals(voucher.getMaxRedemptions())) {
            voucher.setStatus(VoucherStatus.USED_UP);
        }
        voucherRepository.save(voucher);
    }
}
