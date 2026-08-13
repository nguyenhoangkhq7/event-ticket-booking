package com.geekup.eventticketbookingservice.voucher;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(nullable = false)
    private Integer maxRedemptions;

    @Column(nullable = false)
    private Integer redeemedCount;

    @Column(nullable = false)
    private Integer maxPerUser;

    @Column(nullable = false)
    private ZonedDateTime startsAt;

    @Column(nullable = false)
    private ZonedDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private ZonedDateTime createdAt;
}
