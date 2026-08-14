package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;

@Entity
@Table(name = "concerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Concert extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private ZonedDateTime startAt;

    private ZonedDateTime endAt;

    @Column(nullable = false)
    private ZonedDateTime saleStartAt;

    @Column(nullable = false)
    private ZonedDateTime saleEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConcertStatus status;

    public Concert(Long id, String name, String description, String venue,
                   ZonedDateTime startAt, ZonedDateTime endAt,
                   ZonedDateTime saleStartAt, ZonedDateTime saleEndAt,
                   ConcertStatus status, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        super(createdAt, updatedAt);
        this.id = id;
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.startAt = startAt;
        this.endAt = endAt;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
        this.status = status;
    }
}
