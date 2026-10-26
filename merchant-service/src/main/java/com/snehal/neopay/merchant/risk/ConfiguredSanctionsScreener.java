package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Development screener: case-insensitive substring match against a configured list.
 * In production this is replaced by a provider call behind the same interface.
 */
@Component
public class ConfiguredSanctionsScreener implements SanctionsScreener {

    private final List<String> sanctionedNames;

    public ConfiguredSanctionsScreener(
            @Value("${risk.sanctions.names:}") String names) {
        this.sanctionedNames = Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    @Override
    public boolean isSanctioned(String name) {
        if (name == null || name.isBlank() || sanctionedNames.isEmpty()) {
            return false;
        }
        String normalized = name.toLowerCase();
        return sanctionedNames.stream().anyMatch(normalized::contains);
    }
}
