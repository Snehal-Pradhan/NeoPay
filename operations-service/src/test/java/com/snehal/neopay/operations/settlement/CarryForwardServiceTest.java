package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.operations.entity.CarryForwardBalance;
import com.snehal.neopay.operations.repository.CarryForwardRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CarryForwardServiceTest {

    private final CarryForwardRepository repository = mock(CarryForwardRepository.class);
    private final CarryForwardService service = new CarryForwardService(repository);

    @Test
    void outstandingReturnsStoredBalance() {
        UUID merchantId = UUID.randomUUID();
        when(repository.findById(merchantId)).thenReturn(Optional.of(
                CarryForwardBalance.builder().merchantId(merchantId).outstandingUnits(4_500).build()));

        assertEquals(4_500, service.outstanding(merchantId));
    }

    @Test
    void outstandingDefaultsToZeroWhenNoBalanceExists() {
        UUID merchantId = UUID.randomUUID();
        when(repository.findById(merchantId)).thenReturn(Optional.empty());

        assertEquals(0, service.outstanding(merchantId));
    }

    @Test
    void setOutstandingPersistsAmountCurrencyAndTimestamp() {
        UUID merchantId = UUID.randomUUID();

        service.setOutstanding(merchantId, 2_000, "INR");

        ArgumentCaptor<CarryForwardBalance> captor = ArgumentCaptor.forClass(CarryForwardBalance.class);
        verify(repository).save(captor.capture());
        CarryForwardBalance saved = captor.getValue();
        assertEquals(merchantId, saved.getMerchantId());
        assertEquals(2_000, saved.getOutstandingUnits());
        assertEquals("INR", saved.getCurrency());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void clearDeletesTheBalance() {
        UUID merchantId = UUID.randomUUID();

        service.clear(merchantId);

        verify(repository).deleteById(merchantId);
    }
}
