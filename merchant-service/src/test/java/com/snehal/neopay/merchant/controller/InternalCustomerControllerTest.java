package com.snehal.neopay.merchant.controller;

import com.snehal.neopay.common.dto.FindOrCreateCustomerRequest;
import com.snehal.neopay.merchant.service.CustomerService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalCustomerControllerTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final InternalCustomerController controller = new InternalCustomerController(customerService);

    @Test
    void findOrCreateDelegatesEveryField() {
        UUID merchantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(customerService.findOrCreate(merchantId, "buyer@example.com", "Buyer", "+919999999999"))
                .thenReturn(customerId);

        UUID result = controller.findOrCreate(
                new FindOrCreateCustomerRequest(merchantId, "buyer@example.com", "Buyer", "+919999999999"));

        assertEquals(customerId, result);
    }
}
