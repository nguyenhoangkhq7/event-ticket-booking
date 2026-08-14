package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.TicketCategoryResponse;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketInventoryRepository ticketInventoryRepository;
    private final InventoryRedisService inventoryRedisService;

    @Transactional(readOnly = true)
    @Cacheable(value = "ticketCategories", key = "#concertId")
    public List<TicketCategoryResponse> getCategoriesByConcertId(Long concertId) {
        List<TicketCategory> categories = ticketCategoryRepository.findByConcertIdAndStatus(concertId, TicketCategoryStatus.ACTIVE);
        
        return categories.stream().map(category -> {
            Integer availableQty = inventoryRedisService.getAvailable(category.getId());
            if (availableQty == null) {
                var inventoryOpt = ticketInventoryRepository.findById(category.getId());
                if (inventoryOpt.isPresent()) {
                    TicketInventory inventory = inventoryOpt.get();
                    availableQty = Math.max(0, inventory.getTotalQuantity() - inventory.getReservedQuantity() - inventory.getSoldQuantity());
                } else {
                    availableQty = 0;
                }
            }

            return TicketCategoryResponse.builder()
                    .id(category.getId())
                    .concertId(category.getConcertId())
                    .name(category.getName())
                    .price(category.getPrice())
                    .maxPerBooking(category.getMaxPerBooking())
                    .availableQuantity(availableQty)
                    .build();
        }).collect(Collectors.toList());
    }

    @CacheEvict(value = "ticketCategories", key = "#concertId")
    public void evictCategoryCache(Long concertId) {
        // Evicts cached categories for concert
    }
}
