package com.snehal.neopay.payment.controller;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.payment.dto.request.CreateOrderRequest;
import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final MerchantContext merchantContext;

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(MerchantId.of(merchantContext.getMerchantId()), request));
    }
}
