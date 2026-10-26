package com.snehal.neopay.operations.recon;

import com.snehal.neopay.common.util.SignerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconAttestationServiceTest {

    private ReconRunRepository runRepository;
    private ReconBreakRepository breakRepository;
    private ReconAttestationService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(ReconRunRepository.class);
        breakRepository = mock(ReconBreakRepository.class);
        service = new ReconAttestationService(runRepository, breakRepository, new SignerUtil());
        ReflectionTestUtils.setField(service, "signingKey", "test-key");
    }

    @Test
    void attestationIsDeterministicAndSigned() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.of(run(runId)));
        when(breakRepository.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(
                reconBreak(runId, "A", ReconBreakType.MISSING_BANK, ReconBreakStatus.OPEN),
                reconBreak(runId, "B", ReconBreakType.DUPLICATE, ReconBreakStatus.RESOLVED)));

        String first = service.attestation(runId);
        String second = service.attestation(runId);

        assertEquals(first, second);
        assertTrue(first.contains("breaks=2"));
        assertTrue(first.contains("open=1"));
        assertTrue(first.contains("resolved=1"));
        assertTrue(first.contains("digest="));
        assertTrue(first.contains("signature="));
    }

    @Test
    void unknownRunThrows() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThrows(com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                () -> service.attestation(runId));
    }

    private static ReconRun run(UUID id) {
        return ReconRun.builder()
                .id(id)
                .internalCount(3)
                .pspCount(4)
                .bankCount(4)
                .breakCount(2)
                .createdAt(LocalDateTime.now())
                .status(ReconRunStatus.COMPLETED)
                .build();
    }

    private static ReconBreak reconBreak(UUID runId, String reference, ReconBreakType type,
                                         ReconBreakStatus status) {
        return ReconBreak.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .type(type)
                .reference(reference)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
