package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.operations.client.MerchantServiceClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementEngineTest {

    @Test
    void runsSettlementForEveryActiveMerchant() {
        MerchantServiceClient client = mock(MerchantServiceClient.class);
        SettlementTransactionExecutor executor = mock(SettlementTransactionExecutor.class);
        SettlementEngine engine = new SettlementEngine(client, executor);

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(client.listActiveMerchantIds()).thenReturn(List.of(first, second));

        engine.run();

        verify(executor).processForMerchant(eq(first), any(LocalDate.class));
        verify(executor).processForMerchant(eq(second), any(LocalDate.class));
        verify(executor, times(2)).processForMerchant(any(), any());
    }

    @Test
    void emptyMerchantListIsNoOp() {
        MerchantServiceClient client = mock(MerchantServiceClient.class);
        SettlementTransactionExecutor executor = mock(SettlementTransactionExecutor.class);
        SettlementEngine engine = new SettlementEngine(client, executor);
        when(client.listActiveMerchantIds()).thenReturn(List.of());

        engine.run();

        verify(executor, times(0)).processForMerchant(any(), any());
    }
}
