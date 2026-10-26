package com.snehal.neopay.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@AutoConfiguration
public class ZipkinConfig {

    @Bean
    @ConditionalOnProperty(name = "management.zipkin.tracing.endpoint")
    public BytesMessageSender zipkinSender(
            @Value("${management.zipkin.tracing.endpoint}") String endpoint) {
        return URLConnectionSender.create(endpoint);
    }
}
