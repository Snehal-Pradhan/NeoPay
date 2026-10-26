package com.snehal.neopay.payment.risk;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "merchant-service", contextId = "riskClient", path = "/internal/risk", url = "${MERCHANT_SERVICE_URI:}")
public interface RiskClient {

    @PostMapping("/authorize")
    RiskDecisionResponse authorize(@RequestBody RiskAuthorizeRequest request);
}
