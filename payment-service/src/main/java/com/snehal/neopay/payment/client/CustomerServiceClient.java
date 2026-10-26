package com.snehal.neopay.payment.client;

import com.snehal.neopay.common.dto.FindOrCreateCustomerRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "merchant-service", contextId = "customerClient", path = "/internal/customers", url = "${MERCHANT_SERVICE_URI:}")
public interface CustomerServiceClient {

    @PostMapping("/find-or-create")
    UUID findOrCreate(@RequestBody FindOrCreateCustomerRequest request);

}
