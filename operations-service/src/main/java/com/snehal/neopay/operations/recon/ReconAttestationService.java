package com.snehal.neopay.operations.recon;

import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.common.util.SignerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Signed, deterministic summary of a reconciliation run. The same run always produces
 * the same bytes, so two parties can compare digests without exchanging records.
 */
@Service
@RequiredArgsConstructor
public class ReconAttestationService {

    private final ReconRunRepository runRepository;
    private final ReconBreakRepository breakRepository;
    private final SignerUtil signerUtil;

    @Value("${report.signing-key:dev-report-signing-key}")
    private String signingKey;

    @Transactional(readOnly = true)
    public String attestation(UUID runId) {
        ReconRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("ReconRun", runId));
        List<ReconBreak> breaks = breakRepository.findByRunIdOrderByCreatedAtAsc(runId);

        long open = breaks.stream()
                .filter(b -> b.getStatus() == ReconBreakStatus.OPEN)
                .count();
        long resolved = breaks.size() - open;

        StringBuilder canonical = new StringBuilder();
        canonical.append("runId=").append(run.getId()).append('\n')
                .append("internal=").append(run.getInternalCount()).append('\n')
                .append("psp=").append(run.getPspCount()).append('\n')
                .append("bank=").append(run.getBankCount()).append('\n')
                .append("breaks=").append(run.getBreakCount()).append('\n')
                .append("open=").append(open).append('\n')
                .append("resolved=").append(resolved).append('\n')
                .append("digest=").append(digest(breaks)).append('\n');

        String signature = signerUtil.sign(canonical.toString(), signingKey);
        return canonical.append("signature=").append(signature).append('\n').toString();
    }

    private static String digest(List<ReconBreak> breaks) {
        StringBuilder lines = new StringBuilder();
        breaks.stream()
                .sorted((a, b) -> a.getReference().compareTo(b.getReference()))
                .forEach(b -> lines.append(b.getReference()).append(',')
                        .append(b.getType().name()).append(',')
                        .append(b.getStatus().name()).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(lines.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
