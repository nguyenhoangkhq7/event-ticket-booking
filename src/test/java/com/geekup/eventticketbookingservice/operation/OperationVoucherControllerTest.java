package com.geekup.eventticketbookingservice.operation;

import com.geekup.eventticketbookingservice.common.dto.ApiResponse;
import com.geekup.eventticketbookingservice.operation.dto.CreateVoucherCampaignRequest;
import com.geekup.eventticketbookingservice.voucher.DiscountType;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationVoucherController Unit Tests")
class OperationVoucherControllerTest {

    @Mock
    private OperationService operationService;

    @InjectMocks
    private OperationVoucherController operationVoucherController;

    private Voucher testVoucher;

    @BeforeEach
    void setUp() {
        testVoucher = Voucher.builder()
                .id(100L)
                .name("Summer Promo")
                .code("SUMMER2026")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15.00"))
                .maxRedemptions(100)
                .redeemedCount(0)
                .maxPerUser(1)
                .startsAt(ZonedDateTime.now())
                .endsAt(ZonedDateTime.now().plusDays(7))
                .status(VoucherStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("createVoucher returns 200 OK and created voucher")
    void createVoucher_Success() {
        CreateVoucherCampaignRequest request = new CreateVoucherCampaignRequest();
        request.setName("Summer Promo");
        request.setDiscountType("PERCENTAGE");
        request.setDiscountValue(new BigDecimal("15.00"));
        request.setMaxRedemptions(100);
        request.setMaxPerUser(1);
        request.setStartsAt(ZonedDateTime.now());
        request.setEndsAt(ZonedDateTime.now().plusDays(7));

        when(operationService.createVoucher(
                eq("Summer Promo"),
                eq("SUMMER2026"),
                eq(DiscountType.PERCENTAGE),
                eq(new BigDecimal("15.00")),
                eq(100),
                eq(1),
                any(ZonedDateTime.class),
                any(ZonedDateTime.class)
        )).thenReturn(testVoucher);

        ResponseEntity<ApiResponse<Voucher>> response = operationVoucherController.createVoucher(request, "SUMMER2026");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("SUMMER2026", response.getBody().getData().getCode());
        verify(operationService, times(1)).createVoucher(
                eq("Summer Promo"),
                eq("SUMMER2026"),
                eq(DiscountType.PERCENTAGE),
                eq(new BigDecimal("15.00")),
                eq(100),
                eq(1),
                any(ZonedDateTime.class),
                any(ZonedDateTime.class)
        );
    }

    @Test
    @DisplayName("disableVoucher returns 200 OK and disabled voucher")
    void disableVoucher_Success() {
        testVoucher.setStatus(VoucherStatus.DISABLED);
        when(operationService.disableVoucher(100L)).thenReturn(testVoucher);

        ResponseEntity<ApiResponse<Voucher>> response = operationVoucherController.disableVoucher(100L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(VoucherStatus.DISABLED, response.getBody().getData().getStatus());
        verify(operationService, times(1)).disableVoucher(100L);
    }

    @Test
    @DisplayName("enableVoucher returns 200 OK and enabled voucher")
    void enableVoucher_Success() {
        testVoucher.setStatus(VoucherStatus.ACTIVE);
        when(operationService.enableVoucher(100L)).thenReturn(testVoucher);

        ResponseEntity<ApiResponse<Voucher>> response = operationVoucherController.enableVoucher(100L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(VoucherStatus.ACTIVE, response.getBody().getData().getStatus());
        verify(operationService, times(1)).enableVoucher(100L);
    }
}
