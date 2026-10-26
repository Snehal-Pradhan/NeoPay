---------------------------- MODULE PaymentLifecycle ----------------------------
EXTENDS Naturals, TLC

CONSTANTS Amount, PartialRefund

VARIABLES status, captured, refunded, settled, ledgerDebit, ledgerCredit

vars == <<status, captured, refunded, settled, ledgerDebit, ledgerCredit>>

Statuses == {"CREATED", "AUTHORIZING", "AUTHORIZED", "CAPTURING",
             "CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "SETTLED", "FAILED"}

TypeInvariant ==
    /\ status \in Statuses
    /\ captured \in 0..Amount
    /\ refunded \in 0..Amount
    /\ captured <= Amount
    /\ ledgerDebit = ledgerCredit

Init ==
    /\ status = "CREATED"
    /\ captured = 0
    /\ refunded = 0
    /\ settled = FALSE
    /\ ledgerDebit = 0
    /\ ledgerCredit = 0

Authorize ==
    /\ status = "CREATED"
    /\ status' = "AUTHORIZING"
    /\ UNCHANGED <<captured, refunded, settled, ledgerDebit, ledgerCredit>>

Approve ==
    /\ status = "AUTHORIZING"
    /\ status' = "AUTHORIZED"
    /\ UNCHANGED <<captured, refunded, settled, ledgerDebit, ledgerCredit>>

Fail ==
    /\ status = "AUTHORIZING"
    /\ status' = "FAILED"
    /\ UNCHANGED <<captured, refunded, settled, ledgerDebit, ledgerCredit>>

CaptureRequest ==
    /\ status = "AUTHORIZED"
    /\ status' = "CAPTURING"
    /\ UNCHANGED <<captured, refunded, settled, ledgerDebit, ledgerCredit>>

Capture ==
    /\ status = "CAPTURING"
    /\ captured' = Amount
    /\ ledgerDebit' = ledgerDebit + Amount
    /\ ledgerCredit' = ledgerCredit + Amount
    /\ status' = "CAPTURED"
    /\ UNCHANGED <<refunded, settled>>

Settle ==
    /\ status = "CAPTURED"
    /\ ~settled
    /\ settled' = TRUE
    /\ status' = "SETTLED"
    /\ UNCHANGED <<captured, refunded, ledgerDebit, ledgerCredit>>

RefundPartial ==
    /\ status \in {"CAPTURED", "SETTLED"}
    /\ refunded + PartialRefund <= captured
    /\ refunded' = refunded + PartialRefund
    /\ status' = IF refunded' = captured THEN "REFUNDED" ELSE "PARTIALLY_REFUNDED"
    /\ UNCHANGED <<captured, settled, ledgerDebit, ledgerCredit>>

Next ==
    \/ Authorize
    \/ Approve
    \/ Fail
    \/ CaptureRequest
    \/ Capture
    \/ Settle
    \/ RefundPartial

Spec == Init /\ [][Next]_vars

\* Invariants

MoneyConserved == ledgerDebit = ledgerCredit

SettleRequiresCapture == settled => captured = Amount

RefundBounded == refunded <= captured

NoCaptureWithoutAuthorization ==
    captured > 0 => status \in {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "SETTLED"}

NoRefundBeforeCapture ==
    refunded > 0 => captured > 0

=============================================================================
