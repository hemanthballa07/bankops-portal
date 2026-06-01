package com.bankops.portal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bankops.portal.dto.ReportSummaryDto;
import com.bankops.portal.service.ReportsService;

import lombok.RequiredArgsConstructor;

/** Read-only ops analytics. Secured by the global URL rules (authenticated). */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping("/summary")
    public ReportSummaryDto getSummary() {
        return reportsService.getSummary();
    }
}
