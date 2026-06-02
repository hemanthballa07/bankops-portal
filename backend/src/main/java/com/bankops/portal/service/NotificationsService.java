package com.bankops.portal.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.bankops.portal.dto.CaseKpiDto;
import com.bankops.portal.dto.NotificationCategory;
import com.bankops.portal.dto.NotificationDto;
import com.bankops.portal.dto.NotificationSeverity;
import com.bankops.portal.dto.NotificationsSummaryDto;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.LogEvent;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.sla.SlaStatus;
import com.bankops.portal.statemachine.CaseState;

import lombok.RequiredArgsConstructor;

/**
 * Derives the ops notifications feed on-read from live state (HELD transactions, active
 * cases, case KPIs). No persistence — the feed self-clears as ops act on items. Each
 * section is independently fail-soft so one bad query degrades that section only.
 */
@Service
@RequiredArgsConstructor
public class NotificationsService {

    private static final int MAX_ITEMS = 50;

    private final TransactionService transactionService;
    private final SupportCaseRepository caseRepository;
    private final CaseService caseService;
    private final LoggingService loggingService;

    public NotificationsSummaryDto getNotifications() {
        List<NotificationDto> items = new ArrayList<>();
        items.addAll(safe(this::fraudHoldItems, "fraud_hold"));
        items.addAll(safe(this::caseItems, "case"));
        NotificationDto backlog = safeBacklog();
        if (backlog != null) {
            items.add(backlog);
        }

        long critical = countBySeverity(items, NotificationSeverity.CRITICAL);
        long warning = countBySeverity(items, NotificationSeverity.WARNING);
        long info = countBySeverity(items, NotificationSeverity.INFO);

        items.sort(Comparator
                .comparingInt((NotificationDto i) -> severityRank(i.getSeverity()))
                .thenComparing(NotificationDto::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        List<NotificationDto> capped = items.size() > MAX_ITEMS
                ? new ArrayList<>(items.subList(0, MAX_ITEMS))
                : items;

        return NotificationsSummaryDto.builder()
                .items(capped)
                .counts(NotificationsSummaryDto.Counts.builder()
                        .critical(critical).warning(warning).info(info)
                        .total(critical + warning + info)
                        .build())
                .build();
    }

    private List<NotificationDto> fraudHoldItems() {
        List<NotificationDto> out = new ArrayList<>();
        for (TransactionDto t : transactionService
                .getTransactionsByStatus(Transaction.TransactionStatus.HELD)) {
            out.add(NotificationDto.builder()
                    .id("HELD-" + t.getId())
                    .category(NotificationCategory.FRAUD_HOLD)
                    .severity(NotificationSeverity.CRITICAL)
                    .title("Fraud hold: $" + t.getAmount() + " on account " + t.getAccountId())
                    .detail(t.getDescription() != null ? t.getDescription() : "Awaiting fraud review")
                    .entityType("TRANSACTION")
                    .entityId(t.getId())
                    .link("/fraud-review")
                    .timestamp(t.getCreatedAt())
                    .build());
        }
        return out;
    }

    private List<NotificationDto> caseItems() {
        List<NotificationDto> out = new ArrayList<>();
        for (SupportCase c : caseRepository.findAll()) {
            if (c.getState() == CaseState.RESOLVED || c.getState() == CaseState.CLOSED) {
                continue;
            }
            NotificationDto item = caseToItem(c);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** One item per case. SLA risk (breached/at-risk) takes precedence over unassigned-HIGH. */
    private NotificationDto caseToItem(SupportCase c) {
        if (c.getSlaStatus() == SlaStatus.BREACHED) {
            return caseItem(c, NotificationCategory.SLA_RISK, NotificationSeverity.CRITICAL,
                    "SLA breached: case #" + c.getId());
        }
        if (c.getSlaStatus() == SlaStatus.AT_RISK) {
            return caseItem(c, NotificationCategory.SLA_RISK, NotificationSeverity.WARNING,
                    "SLA at risk: case #" + c.getId());
        }
        if (c.getAssignee() == null && c.getSeverity() == SupportCase.CaseSeverity.HIGH) {
            return caseItem(c, NotificationCategory.CASE_UNASSIGNED, NotificationSeverity.WARNING,
                    "Unassigned high-severity case #" + c.getId());
        }
        return null;
    }

    private NotificationDto caseItem(SupportCase c, NotificationCategory category,
            NotificationSeverity severity, String title) {
        return NotificationDto.builder()
                .id("CASE-" + c.getId())
                .category(category)
                .severity(severity)
                .title(title)
                .detail(c.getSummary())
                .entityType("CASE")
                .entityId(c.getId())
                .link("/cases")
                .timestamp(c.getCreatedAt())
                .build();
    }

    private NotificationDto safeBacklog() {
        try {
            CaseKpiDto kpis = caseService.getKpis();
            return NotificationDto.builder()
                    .id("BACKLOG")
                    .category(NotificationCategory.BACKLOG)
                    .severity(NotificationSeverity.INFO)
                    .title("Case backlog")
                    .detail(kpis.getOpenCases() + " open · " + kpis.getUnassignedCases() + " unassigned")
                    .entityType("CASE")
                    .link("/cases")
                    .build();
        } catch (RuntimeException e) {
            logError("backlog", e);
            return null;
        }
    }

    private List<NotificationDto> safe(Supplier<List<NotificationDto>> section, String name) {
        try {
            return section.get();
        } catch (RuntimeException e) {
            logError(name, e);
            return List.of();
        }
    }

    private void logError(String section, RuntimeException e) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("section", section);
        ctx.put("error", e.getClass().getSimpleName());
        if (e.getMessage() != null) {
            ctx.put("message", e.getMessage());
        }
        loggingService.logEvent("notifications", LogEvent.LogLevel.WARN,
                "notifications.section_error", ctx);
    }

    private static long countBySeverity(List<NotificationDto> items, NotificationSeverity s) {
        return items.stream().filter(i -> i.getSeverity() == s).count();
    }

    private static int severityRank(NotificationSeverity s) {
        return switch (s) {
            case CRITICAL -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }
}
