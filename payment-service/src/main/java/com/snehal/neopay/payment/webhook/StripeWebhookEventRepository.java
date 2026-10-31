package com.snehal.neopay.payment.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, UUID> {

    Optional<StripeWebhookEvent> findByProviderAndExternalEventId(String provider, String externalEventId);
}
