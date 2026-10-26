package com.snehal.neopay.payment.risk;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.exception.RiskRejectedException;
import com.snehal.neopay.common.exception.StepUpRequiredException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Applies the merchant risk decision before a payment is recorded.
 *
 * <p>When {@code risk.enforcement-enabled} is false the client is not called, so local
 * development is unaffected. When the merchant service is unreachable the check fails
 * open and increments {@code risk_unavailable_total}; this is documented behaviour, not
 * a silent swallow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskEnforcer {

    private final RiskClient riskClient;
    private final MeterRegistry registry;

    @Value("${risk.enforcement-enabled:false}")
    private boolean enforcementEnabled;

    public void enforce(UUID merchantId, Money amount) {
        if (!enforcementEnabled) {
            return;
        }
        try {
            RiskDecisionResponse decision = riskClient.authorize(
                    new RiskAuthorizeRequest(merchantId, amount.getAmountUnits()));
            if (!decision.allowed()) {
                throw new RiskRejectedException(decision.reason());
            }
            if (decision.stepUpRequired()) {
                throw new StepUpRequiredException();
            }
        } catch (RiskRejectedException | StepUpRequiredException decision) {
            throw decision;
        } catch (Exception unavailable) {
            log.warn("Risk service unavailable, failing open for merchantId={}", merchantId, unavailable);
            Counter.builder("risk_unavailable_total").register(registry).increment();
        }
    }
}
