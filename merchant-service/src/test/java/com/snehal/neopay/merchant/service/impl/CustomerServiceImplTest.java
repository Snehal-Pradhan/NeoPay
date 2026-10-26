package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.entity.Customer;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.repository.CustomerRepository;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceImplTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final CustomerServiceImpl service =
            new CustomerServiceImpl(customerRepository, merchantRepository);

    @Test
    void blankEmailReturnsNull() {
        assertNull(service.findOrCreate(UUID.randomUUID(), "  ", "N", "P"));
        assertNull(service.findOrCreate(UUID.randomUUID(), null, "N", "P"));
    }

    @Test
    void existingCustomerIsReused() {
        UUID merchantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByMerchant_IdAndEmail(merchantId, "a@b.com"))
                .thenReturn(Optional.of(Customer.builder().id(customerId).build()));

        assertEquals(customerId, service.findOrCreate(merchantId, "a@b.com", "N", "P"));
        verify(customerRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void newCustomerIsCreated() {
        UUID merchantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByMerchant_IdAndEmail(merchantId, "new@b.com"))
                .thenReturn(Optional.empty());
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(mock(Merchant.class)));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(customerId);
            return customer;
        });

        assertEquals(customerId, service.findOrCreate(merchantId, "new@b.com", "N", "P"));
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void missingMerchantIsRejected() {
        UUID merchantId = UUID.randomUUID();
        when(customerRepository.findByMerchant_IdAndEmail(merchantId, "x@b.com"))
                .thenReturn(Optional.empty());
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.findOrCreate(merchantId, "x@b.com", "N", "P"));
    }
}
