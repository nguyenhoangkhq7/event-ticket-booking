package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.catalog.Concert;
import com.geekup.eventticketbookingservice.catalog.TicketCategory;
import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.operation.dto.CreateConcertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operation/concerts")
@RequiredArgsConstructor
public class OperationConcertController {

    private final OperationService operationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Concert>> createConcert(@RequestBody CreateConcertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(operationService.createConcert(request)));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<Concert>> publishConcert(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(operationService.publishConcert(id)));
    }

    @PostMapping("/{id}/ticket-categories")
    public ResponseEntity<ApiResponse<TicketCategory>> addTicketCategory(@PathVariable Long id, @RequestBody TicketCategory category) {
        return ResponseEntity.ok(ApiResponse.success(operationService.addTicketCategory(id, category)));
    }

    @PostMapping("/{id}/ticket-categories/{categoryId}/inventory")
    public ResponseEntity<ApiResponse<TicketInventory>> setInventory(@PathVariable Long id, @PathVariable Long categoryId, @RequestParam int totalQuantity) {
        return ResponseEntity.ok(ApiResponse.success(operationService.setInventory(categoryId, totalQuantity)));
    }
}
