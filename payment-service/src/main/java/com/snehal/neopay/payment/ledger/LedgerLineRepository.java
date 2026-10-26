package com.snehal.neopay.payment.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerLineRepository extends JpaRepository<LedgerLine, UUID> {

    @Query("""
            select l.transactionId from LedgerLine l
            group by l.transactionId
            having sum(l.debitUnits) <> sum(l.creditUnits)
            """)
    List<UUID> findUnbalancedTransactions();

    @Query("select coalesce(sum(l.debitUnits), 0) from LedgerLine l")
    long totalDebits();

    @Query("select coalesce(sum(l.creditUnits), 0) from LedgerLine l")
    long totalCredits();

    @Query("""
            select coalesce(sum(l.creditUnits), 0) - coalesce(sum(l.debitUnits), 0)
            from LedgerLine l where l.account = :account
            """)
    long balanceOf(@Param("account") LedgerAccount account);

    List<LedgerLine> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    boolean existsByTransactionIdAndAccount(UUID transactionId, LedgerAccount account);

    @Query("""
            select l from LedgerLine l
            where l.account = :account and l.creditUnits > 0 and l.postedAt < :cutoff
              and l.id not in (select r.sourceLineId from ReserveRelease r)
            """)
    List<LedgerLine> findReleasableReserveLines(@Param("account") LedgerAccount account,
                                                @Param("cutoff") java.time.LocalDateTime cutoff);

    @Query("""
            select l from LedgerLine l
            where l.postedAt >= :from and l.postedAt < :to
            order by l.account, l.transactionId, l.id
            """)
    List<LedgerLine> findForPeriod(@Param("from") java.time.LocalDateTime from,
                                   @Param("to") java.time.LocalDateTime to);
}
