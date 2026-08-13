package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.operation.dto.CreateVoucherCampaignRequest;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operation/vouchers")
@RequiredArgsConstructor
public class OperationVoucherController {

    private final OperationService operationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Voucher>> createVoucher(@RequestBody CreateVoucherCampaignRequest request, @RequestParam(required = false) String code) {
        return ResponseEntity.ok(ApiResponse.success(operationService.createVoucher(
            request.getName(),
            code,
            DiscountType.valueOf(request.getDiscountType()),
            request.getDiscountValue(),
            request.getMaxRedemptions(),
            request.getMaxPerUser(),
            request.getStartsAt(),
            request.getEndsAt()
        )));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Voucher>> disableVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(operationService.disableVoucher(id)));
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Voucher>> enableVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(operationService.enableVoucher(id)));
    }
}
