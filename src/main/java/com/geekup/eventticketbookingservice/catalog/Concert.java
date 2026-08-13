package com.geekup.eventticketbookingservice.catalog;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "concerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concert {
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

    @CreationTimestamp
    @Column(updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    private ZonedDateTime updatedAt;
}
