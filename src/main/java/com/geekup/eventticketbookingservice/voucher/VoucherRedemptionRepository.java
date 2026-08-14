package com.geekup.eventticketbookingservice.voucher;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {
    boolean existsByVoucherIdAndUserId(Long voucherId, Long userId);
    void deleteByVoucherIdAndBookingId(Long voucherId, Long bookingId);
}
