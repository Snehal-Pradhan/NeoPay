package com.snehal.neopay.operations.recon;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconControllerTest {

    private final ReconService reconService = mock(ReconService.class);
    private final ReconAttestationService attestationService = mock(ReconAttestationService.class);
    private final ReconController controller = new ReconController(reconService, attestationService);

    private static ReconRun run() {
        return ReconRun.builder()
                .id(UUID.randomUUID())
                .internalCount(10)
                .pspCount(11)
                .bankCount(9)
                .breakCount(3)
                .createdAt(LocalDateTime.now())
                .status(ReconRunStatus.COMPLETED)
                .build();
    }

    private static ReconBreak reconBreak(UUID runId) {
        return ReconBreak.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .type(ReconBreakType.AMOUNT_MISMATCH_PSP)
                .reference("pay_123")
                .internalAmount(1_000L)
                .pspAmount(900L)
                .currency("INR")
                .status(ReconBreakStatus.OPEN)
                .build();
    }

    @Test
    void runReturns201WithCounts() {
        ReconRun run = run();
        when(reconService.run("psp.csv", "bank.csv")).thenReturn(run);

        var response = controller.run(new ReconDtos.ReconRequest("psp.csv", "bank.csv"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(run.getId(), response.getBody().id());
        assertEquals(10, response.getBody().internalCount());
        assertEquals(3, response.getBody().breakCount());
    }

    @Test
    void breaksMapTypeAndStatusNames() {
        UUID runId = UUID.randomUUID();
        ReconBreak reconBreak = reconBreak(runId);
        when(reconService.breaks(runId)).thenReturn(List.of(reconBreak));

        List<ReconDtos.ReconBreakResponse> response = controller.breaks(runId);

        assertEquals(1, response.size());
        assertEquals("AMOUNT_MISMATCH_PSP", response.get(0).type());
        assertEquals("OPEN", response.get(0).status());
        assertEquals(900L, response.get(0).pspAmount());
    }

    @Test
    void resolveReturnsUpdatedBreak() {
        UUID breakId = UUID.randomUUID();
        ReconBreak resolved = reconBreak(UUID.randomUUID());
        when(reconService.resolveBreak(breakId, "matched against bank statement")).thenReturn(resolved);

        ReconDtos.ReconBreakResponse response = controller.resolve(
                breakId, new ReconDtos.ResolveRequest("matched against bank statement"));

        assertSame(resolved.getId(), response.id());
    }

    @Test
    void attestationIsPlainText() {
        UUID runId = UUID.randomUUID();
        when(attestationService.attestation(runId)).thenReturn("run=" + runId + " balanced=true");

        assertEquals("run=" + runId + " balanced=true", controller.attestation(runId));
    }
}
