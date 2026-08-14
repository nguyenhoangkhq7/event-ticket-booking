package com.geekup.eventticketbookingservice.catalog;

import com.geekup.eventticketbookingservice.catalog.dto.ConcertResponse;
import com.geekup.eventticketbookingservice.catalog.dto.TicketCategoryResponse;
import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;
    private final TicketCategoryService ticketCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConcertResponse>>> getPublishedConcerts(
            @PageableDefault(page = 0, size = 20, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(concertService.getPublishedConcerts(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConcertResponse>> getConcertById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(concertService.getConcertById(id)));
    }

    @GetMapping("/{id}/ticket-categories")
    public ResponseEntity<ApiResponse<List<TicketCategoryResponse>>> getTicketCategories(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ticketCategoryService.getCategoriesByConcertId(id)));
    }
}
