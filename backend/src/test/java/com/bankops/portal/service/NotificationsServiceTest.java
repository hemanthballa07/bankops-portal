package com.bankops.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bankops.portal.dto.CaseKpiDto;
import com.bankops.portal.dto.NotificationCategory;
import com.bankops.portal.dto.NotificationSeverity;
import com.bankops.portal.dto.NotificationsSummaryDto;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.repository.SupportCaseRepository;
import com.bankops.portal.sla.SlaStatus;
import com.bankops.portal.statemachine.CaseState;

class NotificationsServiceTest {

    private TransactionService transactionService;
    private SupportCaseRepository caseRepository;
    private CaseService caseService;
    private LoggingService loggingService;
    private NotificationsService service;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        caseRepository = mock(SupportCaseRepository.class);
        caseService = mock(CaseService.class);
        loggingService = mock(LoggingService.class);
        service = new NotificationsService(transactionService, caseRepository, caseService, loggingService);
        when(caseService.getKpis()).thenReturn(CaseKpiDto.builder()
                .openCases(3).unassignedCases(2).slaAtRiskCases(0)
                .highSeverityCases(1).unassignedHighSeverity(1).build());
        when(transactionService.getTransactionsByStatus(any())).thenReturn(List.of());
        when(caseRepository.findAll()).thenReturn(List.of());
    }

    private TransactionDto heldTx(long id) {
        TransactionDto t = new TransactionDto();
        t.setId(id);
        t.setAccountId(1L);
        t.setAmount(new BigDecimal("99999.00"));
        t.setStatus("HELD");
        t.setDescription("luxury car");
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private SupportCase caseWith(long id, SupportCase.CaseSeverity sev, CaseState state,
            SlaStatus sla, boolean assigned) {
        return SupportCase.builder()
                .id(id)
                .severity(sev)
                .state(state)
                .slaStatus(sla)
                .summary("case " + id)
                .createdAt(LocalDateTime.now())
                .assignee(assigned ? com.bankops.portal.entity.Agent.builder().id(9L).build() : null)
                .build();
    }

    @Test
    void heldTransaction_becomesCriticalFraudHold() {
        when(transactionService.getTransactionsByStatus(any())).thenReturn(List.of(heldTx(42L)));

        NotificationsSummaryDto out = service.getNotifications();

        assertThat(out.getItems()).anySatisfy(i -> {
            assertThat(i.getId()).isEqualTo("HELD-42");
            assertThat(i.getCategory()).isEqualTo(NotificationCategory.FRAUD_HOLD);
            assertThat(i.getSeverity()).isEqualTo(NotificationSeverity.CRITICAL);
            assertThat(i.getLink()).isEqualTo("/fraud-review");
        });
        assertThat(out.getCounts().getCritical()).isEqualTo(1);
    }

    @Test
    void unassignedHighCase_becomesWarning_andBreachedSlaTakesPrecedence() {
        when(caseRepository.findAll()).thenReturn(List.of(
                caseWith(7L, SupportCase.CaseSeverity.HIGH, CaseState.NEW, null, false),
                caseWith(8L, SupportCase.CaseSeverity.HIGH, CaseState.NEW, SlaStatus.BREACHED, false)
        ));

        NotificationsSummaryDto out = service.getNotifications();

        assertThat(out.getItems()).anySatisfy(i -> {
            assertThat(i.getId()).isEqualTo("CASE-7");
            assertThat(i.getCategory()).isEqualTo(NotificationCategory.CASE_UNASSIGNED);
            assertThat(i.getSeverity()).isEqualTo(NotificationSeverity.WARNING);
        });
        assertThat(out.getItems()).anySatisfy(i -> {
            assertThat(i.getId()).isEqualTo("CASE-8");
            assertThat(i.getCategory()).isEqualTo(NotificationCategory.SLA_RISK);
            assertThat(i.getSeverity()).isEqualTo(NotificationSeverity.CRITICAL);
        });
        assertThat(out.getItems().stream().filter(i -> "CASE-8".equals(i.getId())).count()).isEqualTo(1);
    }

    @Test
    void resolvedCase_isExcluded() {
        when(caseRepository.findAll()).thenReturn(List.of(
                caseWith(9L, SupportCase.CaseSeverity.HIGH, CaseState.RESOLVED, SlaStatus.BREACHED, false)));

        NotificationsSummaryDto out = service.getNotifications();

        assertThat(out.getItems()).noneMatch(i -> "CASE-9".equals(i.getId()));
    }

    @Test
    void backlog_isInfoItem_excludedFromActionableCounts() {
        NotificationsSummaryDto out = service.getNotifications();

        assertThat(out.getItems()).anySatisfy(i -> {
            assertThat(i.getCategory()).isEqualTo(NotificationCategory.BACKLOG);
            assertThat(i.getSeverity()).isEqualTo(NotificationSeverity.INFO);
            assertThat(i.getDetail()).isEqualTo("3 open · 2 unassigned");
        });
        assertThat(out.getCounts().getCritical()).isZero();
        assertThat(out.getCounts().getWarning()).isZero();
        assertThat(out.getCounts().getInfo()).isEqualTo(1);
    }

    @Test
    void sectionFailure_degradesOnlyThatSection() {
        when(transactionService.getTransactionsByStatus(any()))
                .thenThrow(new RuntimeException("boom"));
        when(caseRepository.findAll()).thenReturn(List.of(
                caseWith(5L, SupportCase.CaseSeverity.HIGH, CaseState.NEW, null, false)));

        NotificationsSummaryDto out = service.getNotifications();

        assertThat(out.getItems()).anyMatch(i -> "CASE-5".equals(i.getId()));
        assertThat(out.getItems()).anyMatch(i -> i.getCategory() == NotificationCategory.BACKLOG);
    }
}
