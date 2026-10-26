package com.snehal.neopay.payment.ledger;

import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import com.snehal.neopay.common.entity.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Append-only double-entry ledger.
 *
 * <p>Every public method writes balanced pairs. There is no update or delete path.
 * Posting the same transaction and credit account twice is a no-op, which makes
 * replay safe without a separate idempotency store.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerLineRepository repository;

    /**
     * Posts a balanced pair: one debit line and one credit line.
     * Idempotent on {@code (transactionId, creditAccount)}.
     *
     * @return the transaction id
     */
    @Transactional
    public UUID postPair(UUID transactionId, LedgerAccount debit, LedgerAccount credit,
                         Money amount, LedgerReference reference) {
        requireTransaction(transactionId);
        requireAccounts(debit, credit);
        long units = amount.getAmountUnits();
        if (units <= 0) {
            throw new IllegalArgumentException("Ledger amount must be positive: " + units);
        }
        if (repository.existsByTransactionIdAndAccount(transactionId, credit)) {
            log.debug("Ledger pair already posted, skipping transaction={} account={}",
                    transactionId, credit);
            return transactionId;
        }

        PostingContext context = new PostingContext(transactionId, amount.getCurrency(),
                reference, LocalDateTime.now());
        repository.saveAll(List.of(
                line(context, debit, units, 0),
                line(context, credit, 0, units)
        ));
        return transactionId;
    }

    @Transactional
    public UUID postCapture(UUID paymentId, Money amount) {
        return postPair(paymentId, LedgerAccount.CUSTOMER_FUNDS, LedgerAccount.MERCHANT_PAYABLE,
                amount, LedgerReference.payment(paymentId));
    }

    @Transactional
    public UUID postRefund(UUID refundId, Money amount, boolean settled) {
        LedgerAccount source = settled
                ? LedgerAccount.BANK_SETTLEMENT
                : LedgerAccount.MERCHANT_PAYABLE;
        return postPair(refundId, source, LedgerAccount.CUSTOMER_FUNDS, amount,
                LedgerReference.refund(refundId));
    }

    @Transactional
    public UUID postPayoutReturn(UUID payoutId, Money amount) {
        return postPair(payoutId, LedgerAccount.BANK_SETTLEMENT, LedgerAccount.MERCHANT_PAYABLE,
                amount, LedgerReference.payoutReturn(payoutId));
    }

    @Transactional
    public UUID postSettlement(SettlementLedgerRequest request) {
        long components = request.feeUnits() + request.gstUnits() + request.tdsUnits()
                + request.reserveUnits() + request.netUnits();
        if (components != request.grossUnits()) {
            throw new IllegalArgumentException("Settlement components do not sum to gross");
        }
        LedgerReference reference = LedgerReference.settlement(request.settlementId());
        UUID transactionId = request.settlementId();

        postIfPositive(transactionId, LedgerAccount.PLATFORM_FEE, request.feeUnits(),
                request.currency(), reference);
        postIfPositive(transactionId, LedgerAccount.GST_PAYABLE, request.gstUnits(),
                request.currency(), reference);
        postIfPositive(transactionId, LedgerAccount.TDS_PAYABLE, request.tdsUnits(),
                request.currency(), reference);
        postIfPositive(transactionId, LedgerAccount.MERCHANT_RESERVE, request.reserveUnits(),
                request.currency(), reference);
        postIfPositive(transactionId, LedgerAccount.BANK_SETTLEMENT, request.netUnits(),
                request.currency(), reference);

        return transactionId;
    }

    private void postIfPositive(UUID transactionId, LedgerAccount credit, long units,
                                String currency, LedgerReference reference) {
        if (units < 0) {
            throw new IllegalArgumentException("Settlement component must be non-negative");
        }
        if (units > 0) {
            postPair(transactionId, LedgerAccount.MERCHANT_PAYABLE, credit,
                    Money.of(units, currency), reference);
        }
    }

    private static void requireTransaction(UUID transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction id is required");
        }
    }

    private static void requireAccounts(LedgerAccount debit, LedgerAccount credit) {
        if (debit == null || credit == null) {
            throw new IllegalArgumentException("Both accounts are required");
        }
        if (debit == credit) {
            throw new IllegalArgumentException("Debit and credit accounts must differ");
        }
    }

    private record PostingContext(UUID transactionId, String currency,
                                  LedgerReference reference, LocalDateTime postedAt) {
    }

    private static LedgerLine line(PostingContext context, LedgerAccount account,
                                   long debit, long credit) {
        return LedgerLine.builder()
                .transactionId(context.transactionId())
                .account(account)
                .debitUnits(debit)
                .creditUnits(credit)
                .currency(context.currency())
                .referenceType(context.reference().type())
                .referenceId(context.reference().id())
                .postedAt(context.postedAt())
                .build();
    }
}
