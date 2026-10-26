package com.snehal.neopay.payment.ledger;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerReferenceTest {

    @Test
    void factoriesTagTheReferenceType() {
        UUID id = UUID.randomUUID();
        assertEquals("PAYMENT", LedgerReference.payment(id).type());
        assertEquals("SETTLEMENT", LedgerReference.settlement(id).type());
        assertEquals("REFUND", LedgerReference.refund(id).type());
        assertEquals("RESERVE_RELEASE", LedgerReference.reserveRelease(id).type());
        assertEquals("DISPUTE", LedgerReference.dispute(id).type());
        assertEquals("PAYOUT_RETURN", LedgerReference.payoutReturn(id).type());
        assertEquals(id, LedgerReference.dispute(id).id());
    }

    @Test
    void typeAndIdAreMandatory() {
        assertThrows(IllegalArgumentException.class,
                () -> new LedgerReference(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new LedgerReference(" ", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new LedgerReference("PAYMENT", null));
    }
}
