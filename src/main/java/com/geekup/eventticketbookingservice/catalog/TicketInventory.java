package com.geekup.eventticketbookingservice.catalog;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "ticket_inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketInventory {
    @Id
    @Column(name = "ticket_category_id")
    private Long ticketCategoryId;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer reservedQuantity;

    @Column(nullable = false)
    private Integer soldQuantity;

    @Version
    @Column(nullable = false)
    private Long version;

    @UpdateTimestamp
    private ZonedDateTime updatedAt;
}
