package com.geekup.eventticketbookingservice.voucher;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "voucher_redemptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherRedemption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long voucherId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long bookingId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @CreationTimestamp
    @Column(updatable = false)
    private ZonedDateTime createdAt;
}
