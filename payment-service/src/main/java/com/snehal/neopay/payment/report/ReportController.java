package com.snehal.neopay.payment.report;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping(value = "/settlement", produces = "text/csv")
    public String settlement(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.settlementReport(date);
    }

    @GetMapping(value = "/tax", produces = "text/csv")
    public String tax(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.taxReport(date);
    }
}
