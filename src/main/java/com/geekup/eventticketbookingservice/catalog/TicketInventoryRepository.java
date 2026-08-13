package com.geekup.eventticketbookingservice.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TicketInventoryRepository extends JpaRepository<TicketInventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TicketInventory i WHERE i.ticketCategoryId = :ticketCategoryId")
    Optional<TicketInventory> findByIdForUpdate(Long ticketCategoryId);
}
