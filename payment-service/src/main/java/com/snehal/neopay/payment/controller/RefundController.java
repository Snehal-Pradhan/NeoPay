package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.payment.dto.request.CreateRefundRequest;
import com.snehal.neopay.payment.dto.response.RefundResponse;
import com.snehal.neopay.payment.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments/{paymentId}/refunds")
public class RefundController {

    private final RefundService refundService;
    private final MerchantContext merchantContext;

    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CreateRefundRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.create(
                MerchantId.of(merchantContext.getMerchantId()),
                PaymentId.of(paymentId), request, idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<List<RefundResponse>> list(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(refundService.list(
                MerchantId.of(merchantContext.getMerchantId()), PaymentId.of(paymentId)));
    }
}
