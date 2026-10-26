package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PayoutReturnLedgerRequest;
import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.operations.client.MerchantServiceClient;
import com.snehal.neopay.operations.client.PaymentServiceClient;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.dto.BankTransferResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Direct unit coverage for the small settlement collaborators. */
class OpsGatewayCoverageTest {

    private final MerchantServiceClient merchantClient = mock(MerchantServiceClient.class);
    private final PaymentServiceClient paymentClient = mock(PaymentServiceClient.class);

    @Test
    void simulatorResolvesEveryPendingSettlement() {
        SettlementRepository repository = mock(SettlementRepository.class);
        SettlementTransactionExecutor executor = mock(SettlementTransactionExecutor.class);
        Settlement settlement = Settlement.builder().id(UUID.randomUUID())
                .merchantId(UUID.randomUUID()).netAmount(Money.inr(100))
                .status(SettlementStatus.TRANSFER_PENDING).build();
        when(repository.findByStatus(SettlementStatus.TRANSFER_PENDING))
                .thenReturn(List.of(settlement));
        BankSettlementCallbackSimulator simulator =
                new BankSettlementCallbackSimulator(repository, executor);

        simulator.processCallbacks();

        verify(executor).resolveTransfer(settlement.getId(), null, null);
    }

    @Test
    void simulatorDoesNothingWhenNoTransferIsPending() {
        SettlementRepository repository = mock(SettlementRepository.class);
        SettlementTransactionExecutor executor = mock(SettlementTransactionExecutor.class);
        when(repository.findByStatus(SettlementStatus.TRANSFER_PENDING)).thenReturn(List.of());

        new BankSettlementCallbackSimulator(repository, executor).processCallbacks();

        verify(executor, never()).resolveTransfer(any(), any(), any());
    }

    @Test
    void bankTransferReturnsRegistrationReference() {
        BankTransferResult result = new BankTransferProcessorImpl().initiate(UUID.randomUUID(),
                UUID.randomUUID(), Money.inr(500), "1234567890", "HDFC0001");

        assertTrue(result.registrationRef().startsWith("TXN_"));
    }

    @Test
    void engineDelegatesToMerchantClientAndExecutor() {
        MerchantServiceClient merchants = mock(MerchantServiceClient.class);
        SettlementTransactionExecutor executor = mock(SettlementTransactionExecutor.class);
        UUID merchantId = UUID.randomUUID();
        when(merchants.listActiveMerchantIds()).thenReturn(List.of(merchantId));
        SettlementEngine engine = new SettlementEngine(merchants, executor);

        engine.run();

        verify(executor).processForMerchant(eq(merchantId), any());
    }

    @Test
    void scheduledEngineCallsRun() {
        MerchantServiceClient merchants = mock(MerchantServiceClient.class);
        when(merchants.listActiveMerchantIds()).thenReturn(List.of());

        new SettlementEngine(merchants, mock(SettlementTransactionExecutor.class)).runScheduled();

        verify(merchants).listActiveMerchantIds();
    }

    @Test
    void gatewayDelegatesEveryRemoteCall() {
        SettlementIntegrationGateway gateway =
                new SettlementIntegrationGateway(merchantClient, paymentClient);
        UUID merchantId = UUID.randomUUID();
        SettlementBankDetails bank = new SettlementBankDetails("123", "IFSC0001", "NeoPay");
        List<PaymentSettlementView> payments = List.of();
        SettlementLedgerRequest ledger = new SettlementLedgerRequest(
                UUID.randomUUID(), 100, 1, 0, 0, 0, 99, "INR");
        PayoutReturnLedgerRequest payout = new PayoutReturnLedgerRequest(
                UUID.randomUUID(), 100, "INR");
        when(paymentClient.findUnsettledCaptured(merchantId)).thenReturn(payments);
        when(merchantClient.getSettlementBankDetails(merchantId)).thenReturn(bank);

        assertEquals(payments, gateway.findUnsettledCaptured(merchantId));
        assertEquals(bank, gateway.getSettlementBankDetails(merchantId));
        gateway.markSettled(List.of(UUID.randomUUID()));
        gateway.postSettlementLedger(ledger);
        gateway.postPayoutReturnLedger(payout);

        verify(paymentClient).markSettled(any());
        verify(paymentClient).postSettlementLedger(ledger);
        verify(paymentClient).postPayoutReturnLedger(payout);
    }
}
