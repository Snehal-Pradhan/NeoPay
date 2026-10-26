package com.snehal.neopay.operations.recon;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/recon")
public class ReconController {

    private final ReconService reconService;
    private final ReconAttestationService attestationService;

    @PostMapping("/runs")
    public ResponseEntity<ReconDtos.ReconRunResponse> run(
            @RequestBody ReconDtos.ReconRequest request) {
        ReconRun run = reconService.run(request.pspCsv(), request.bankCsv());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReconDtos.ReconRunResponse.from(run));
    }

    @GetMapping("/runs/{runId}/breaks")
    public List<ReconDtos.ReconBreakResponse> breaks(@PathVariable UUID runId) {
        return ReconDtos.ReconBreakResponse.from(reconService.breaks(runId));
    }

    @PostMapping("/breaks/{breakId}/resolve")
    public ReconDtos.ReconBreakResponse resolve(@PathVariable UUID breakId,
                                                @RequestBody ReconDtos.ResolveRequest request) {
        return ReconDtos.ReconBreakResponse.from(
                reconService.resolveBreak(breakId, request.note()));
    }

    @GetMapping(value = "/runs/{runId}/attestation", produces = "text/plain")
    public String attestation(@PathVariable UUID runId) {
        return attestationService.attestation(runId);
    }
}
