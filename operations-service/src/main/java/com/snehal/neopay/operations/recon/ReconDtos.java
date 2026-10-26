package com.snehal.neopay.operations.recon;

import java.util.List;
import java.util.UUID;

public final class ReconDtos {

    private ReconDtos() {
    }

    public record ReconRequest(String pspCsv, String bankCsv) {
    }

    public record ResolveRequest(String note) {
    }

    public record ReconRunResponse(UUID id, int internalCount, int pspCount, int bankCount,
                                   int breakCount) {
        public static ReconRunResponse from(ReconRun run) {
            return new ReconRunResponse(run.getId(), run.getInternalCount(), run.getPspCount(),
                    run.getBankCount(), run.getBreakCount());
        }
    }

    public record ReconBreakResponse(UUID id, String type, String reference,
                                     Long internalAmount, Long pspAmount, Long bankAmount,
                                     String currency, String status, String note) {
        public static ReconBreakResponse from(ReconBreak reconBreak) {
            return new ReconBreakResponse(reconBreak.getId(), reconBreak.getType().name(),
                    reconBreak.getReference(), reconBreak.getInternalAmount(),
                    reconBreak.getPspAmount(), reconBreak.getBankAmount(),
                    reconBreak.getCurrency(), reconBreak.getStatus().name(),
                    reconBreak.getNote());
        }

        public static List<ReconBreakResponse> from(List<ReconBreak> breaks) {
            return breaks.stream().map(ReconBreakResponse::from).toList();
        }
    }
}
