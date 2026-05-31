package com.bankops.portal.sla;

import com.bankops.portal.entity.SupportCase;
import com.bankops.portal.entity.SlaStatusChangeEvent;
import com.bankops.portal.repository.SlaStatusChangeEventRepository;
import com.bankops.portal.statemachine.CaseState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Deterministic unit tests for SLA Service using Clock injection.
 */
@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private SlaStatusChangeEventRepository slaEventRepository;
    private SlaConfiguration slaConfiguration;
    private SlaService slaService;
    private Clock fixedClock;
    private SupportCase testCase;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(BASE_TIME, ZoneId.systemDefault());

        slaConfiguration = new SlaConfiguration();
        ReflectionTestUtils.setField(slaConfiguration, "atRiskThreshold", 0.8);
        slaEventRepository = mock(SlaStatusChangeEventRepository.class);

        slaService = new SlaService(slaConfiguration, slaEventRepository, fixedClock);

        testCase = SupportCase.builder()
                .id(1L)
                .state(CaseState.NEW)
                .severity(SupportCase.CaseSeverity.HIGH)
                .summary("Test case")
                .correlationId("test-123")
                .createdAt(LocalDateTime.now(fixedClock))
                .build();
    }

    // ========== Initialization Tests ==========

    @ParameterizedTest(name = "{0} priority -> {1} hours SLA")
    @MethodSource("providePriorities")
    void testInitializeSla_SetsDueDateCorrectly(SlaPriority priority, long expectedHours) {
        // When
        slaService.initializeSla(testCase, priority);

        // Then
        assertNotNull(testCase.getSlaDueAt());
        assertEquals(priority, testCase.getPriority());
        assertEquals(SlaStatus.ON_TRACK, testCase.getSlaStatus());
        assertEquals(0L, testCase.getTotalPausedDurationSeconds());

        LocalDateTime expectedDueAt = LocalDateTime.now(fixedClock).plusHours(expectedHours);
        assertEquals(expectedDueAt, testCase.getSlaDueAt());
    }

    static Stream<Arguments> providePriorities() {
        return Stream.of(
                Arguments.of(SlaPriority.P1, 24L),
                Arguments.of(SlaPriority.P2, 72L),
                Arguments.of(SlaPriority.P3, 168L) // 7 days
        );
    }

    @Test
    void testInitializeSla_AlreadyInitialized_DoesNothing() {
        // Given
        LocalDateTime existingDueAt = LocalDateTime.now(fixedClock).plusDays(1);
        testCase.setSlaDueAt(existingDueAt);

        // When
        slaService.initializeSla(testCase, SlaPriority.P1);

        // Then - due date unchanged
        assertEquals(existingDueAt, testCase.getSlaDueAt());
    }

    // ========== Pause/Resume Tests ==========

    @Test
    void testPauseSla_RecordsPauseTimestamp() {
        // Given
        slaService.initializeSla(testCase, SlaPriority.P2);

        // When
        slaService.pauseSla(testCase);

        // Then
        assertNotNull(testCase.getPausedAt());
        assertEquals(LocalDateTime.now(fixedClock), testCase.getPausedAt());
    }

    @Test
    void testResumeSla_AccumulatesPauseDuration() {
        // Given
        slaService.initializeSla(testCase, SlaPriority.P2);
        LocalDateTime originalDueAt = testCase.getSlaDueAt();

        // Pause at T+0
        slaService.pauseSla(testCase);

        // Advance clock by 2 hours
        Clock advancedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(2)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, advancedClock);

        // When - Resume
        slaService.resumeSla(testCase);

        // Then
        assertNull(testCase.getPausedAt());
        assertEquals(7200L, testCase.getTotalPausedDurationSeconds()); // 2 hours = 7200 seconds

        // Due date should be extended by pause duration
        LocalDateTime expectedNewDueAt = originalDueAt.plusHours(2);
        assertEquals(expectedNewDueAt, testCase.getSlaDueAt());
    }

    @Test
    void testMultiplePauseResumeCycles_AccumulatesCorrectly() {
        // Given
        slaService.initializeSla(testCase, SlaPriority.P2);
        LocalDateTime originalDueAt = testCase.getSlaDueAt();

        // First pause/resume (1 hour)
        slaService.pauseSla(testCase);
        Clock clock1 = Clock.fixed(BASE_TIME.plus(Duration.ofHours(1)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, clock1);
        slaService.resumeSla(testCase);

        // Second pause/resume (30 minutes)
        slaService.pauseSla(testCase);
        Clock clock2 = Clock.fixed(BASE_TIME.plus(Duration.ofMinutes(90)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, clock2);
        slaService.resumeSla(testCase);

        // Then
        assertEquals(5400L, testCase.getTotalPausedDurationSeconds()); // 1.5 hours = 5400 seconds
        LocalDateTime expectedDueAt = originalDueAt.plusMinutes(90);
        assertEquals(expectedDueAt, testCase.getSlaDueAt());
    }

    // ========== Status Computation Tests ==========

    @Test
    void testComputeSlaStatus_OnTrack_LessThan80Percent() {
        // Given - P2 = 72 hours, advance 50 hours (69%)
        slaService.initializeSla(testCase, SlaPriority.P2);

        Clock advancedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(50)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, advancedClock);

        // When
        SlaStatus status = slaService.computeSlaStatus(testCase);

        // Then
        assertEquals(SlaStatus.ON_TRACK, status);
    }

    @Test
    void testComputeSlaStatus_AtRisk_80PercentThreshold() {
        // Given - P2 = 72 hours, advance 58 hours (80.5%)
        slaService.initializeSla(testCase, SlaPriority.P2);

        Clock advancedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(58)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, advancedClock);

        // When
        SlaStatus status = slaService.computeSlaStatus(testCase);

        // Then
        assertEquals(SlaStatus.AT_RISK, status);
    }

    @Test
    void testComputeSlaStatus_Breached_PastDueTime() {
        // Given - P1 = 24 hours, advance 25 hours
        slaService.initializeSla(testCase, SlaPriority.P1);

        Clock advancedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(25)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, advancedClock);

        // When
        SlaStatus status = slaService.computeSlaStatus(testCase);

        // Then
        assertEquals(SlaStatus.BREACHED, status);
    }

    @Test
    void testComputeSlaStatus_PausedTime_NotCounted() {
        // Given - P2 = 72 hours
        slaService.initializeSla(testCase, SlaPriority.P2);

        // Pause for 10 hours
        slaService.pauseSla(testCase);
        Clock pausedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(10)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, pausedClock);
        slaService.resumeSla(testCase);

        // Advance another 60 hours (total real time = 70 hours, but 10 were paused)
        // Effective elapsed = 60 hours = 83% of 72 hours
        Clock finalClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(70)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, finalClock);

        // When
        SlaStatus status = slaService.computeSlaStatus(testCase);

        // Then - Should be AT_RISK (83% > 80%)
        assertEquals(SlaStatus.AT_RISK, status);
    }

    // ========== Update and Audit Tests ==========

    @Test
    void testUpdateSlaStatus_StatusChanged_CreatesAuditEvent() {
        // Given
        slaService.initializeSla(testCase, SlaPriority.P1);
        testCase.setSlaStatus(SlaStatus.ON_TRACK);

        // Advance to breach
        Clock breachedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(25)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, breachedClock);

        when(slaEventRepository.save(any(SlaStatusChangeEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean updated = slaService.updateSlaStatus(testCase);

        // Then
        assertTrue(updated);
        assertEquals(SlaStatus.BREACHED, testCase.getSlaStatus());

        ArgumentCaptor<SlaStatusChangeEvent> eventCaptor = ArgumentCaptor.forClass(SlaStatusChangeEvent.class);
        verify(slaEventRepository).save(eventCaptor.capture());

        SlaStatusChangeEvent event = eventCaptor.getValue();
        assertEquals(1L, event.getCaseId());
        assertEquals(SlaStatus.ON_TRACK, event.getPreviousStatus());
        assertEquals(SlaStatus.BREACHED, event.getNewStatus());
        assertEquals("test-123", event.getCorrelationId());
        assertEquals("SYSTEM", event.getActor());
    }

    @Test
    void testUpdateSlaStatus_NoChange_NoAuditEvent() {
        // Given
        slaService.initializeSla(testCase, SlaPriority.P2);
        testCase.setSlaStatus(SlaStatus.ON_TRACK);

        // When - Still on track
        boolean updated = slaService.updateSlaStatus(testCase);

        // Then
        assertFalse(updated);
        verify(slaEventRepository, never()).save(any());
    }

    // ========== Remaining Time Tests ==========

    @Test
    void testGetRemainingTime_Positive() {
        // Given - P1 = 24 hours, advance 10 hours
        slaService.initializeSla(testCase, SlaPriority.P1);

        Clock advancedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(10)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, advancedClock);

        // When
        Duration remaining = slaService.getRemainingTime(testCase);

        // Then
        assertEquals(Duration.ofHours(14), remaining);
    }

    @Test
    void testGetRemainingTime_Negative_WhenBreached() {
        // Given - P1 = 24 hours, advance 26 hours
        slaService.initializeSla(testCase, SlaPriority.P1);

        Clock advancedClock = Clock.fixed(BASE_TIME.plus(Duration.ofHours(26)), ZoneId.systemDefault());
        slaService = new SlaService(slaConfiguration, slaEventRepository, advancedClock);

        // When
        Duration remaining = slaService.getRemainingTime(testCase);

        // Then
        assertEquals(Duration.ofHours(-2), remaining);
    }
}
