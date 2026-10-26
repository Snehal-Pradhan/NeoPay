package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/v1/payments")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MerchantContext merchantContext;

    @PostMapping
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody PaymentInitRequest request,
                                                    @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiate(MerchantId.of(merchantContext.getMerchantId()), request, idempotencyKey));
    }

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentResponse> capture(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.capture(MerchantId.of(merchantContext.getMerchantId()), PaymentId.of(paymentId)));
    }

}
