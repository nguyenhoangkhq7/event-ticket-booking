package com.geekup.eventticketbookingservice.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {
    List<TicketCategory> findByConcertId(Long concertId);
    List<TicketCategory> findByConcertIdAndStatus(Long concertId, TicketCategoryStatus status);
}
