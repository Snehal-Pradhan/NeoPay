package com.snehal.neopay.common.dto;

import java.util.UUID;

public record WebhookTarget(UUID configId, String targetUrl, String webhookSecret) {

    @Override
    public String toString() {
        return "WebhookTarget[configId=" + configId + ", targetUrl=" + targetUrl
                + ", webhookSecret=***]";
    }
}
