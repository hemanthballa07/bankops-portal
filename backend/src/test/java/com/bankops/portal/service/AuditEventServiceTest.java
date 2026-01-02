package com.bankops.portal.service;

import com.bankops.portal.entity.AuditEvent;
import com.bankops.portal.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditEventService auditEventService;

    @BeforeEach
    void setUp() {
        // ObjectMapper is mocked, so we need to stub its behavior
    }

    @Test
    void testRecordEvent_Success() throws Exception {
        // Arrange
        Map<String, Object> oldValue = new HashMap<>();
        oldValue.put("balance", 100.00);
        Map<String, Object> newValue = new HashMap<>();
        newValue.put("balance", 150.00);

        when(objectMapper.writeValueAsString(oldValue)).thenReturn("{\"balance\":100.00}");
        when(objectMapper.writeValueAsString(newValue)).thenReturn("{\"balance\":150.00}");

        // Act
        auditEventService.recordEvent(
                AuditEvent.EntityType.ACCOUNT,
                1L,
                AuditEvent.Action.UPDATE,
                oldValue,
                newValue,
                "test-correlation-id");

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());

        AuditEvent savedEvent = eventCaptor.getValue();
        assertEquals(AuditEvent.EntityType.ACCOUNT, savedEvent.getEntityType());
        assertEquals(1L, savedEvent.getEntityId());
        assertEquals(AuditEvent.Action.UPDATE, savedEvent.getAction());
        assertEquals("{\"balance\":100.00}", savedEvent.getOldValue());
        assertEquals("{\"balance\":150.00}", savedEvent.getNewValue());
        assertEquals("test-correlation-id", savedEvent.getPerformedBy());
        assertNotNull(savedEvent.getTimestamp());
    }

    @Test
    void testRecordEvent_WithNullValues() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(null)).thenReturn(null);

        // Act
        auditEventService.recordEvent(
                AuditEvent.EntityType.ACCOUNT,
                1L,
                AuditEvent.Action.CREATE,
                null,
                null,
                "system");

        // Assert
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());

        AuditEvent savedEvent = eventCaptor.getValue();
        assertNull(savedEvent.getOldValue());
        assertNull(savedEvent.getNewValue());
    }

    @Test
    void testRecordEvent_JsonSerializationFailure() throws Exception {
        // Arrange
        Map<String, Object> value = new HashMap<>();
        when(objectMapper.writeValueAsString(value))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Test error") {
                });

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            auditEventService.recordEvent(
                    AuditEvent.EntityType.ACCOUNT,
                    1L,
                    AuditEvent.Action.UPDATE,
                    value,
                    value,
                    "test");
        });

        verify(auditEventRepository, never()).save(any());
    }

    @Test
    void testGetAuditTimeline_Pagination() {
        // Arrange
        AuditEvent event1 = AuditEvent.builder()
                .id("1")
                .entityType(AuditEvent.EntityType.ACCOUNT)
                .entityId(1L)
                .action(AuditEvent.Action.UPDATE)
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .id("2")
                .entityType(AuditEvent.EntityType.ACCOUNT)
                .entityId(1L)
                .action(AuditEvent.Action.STATUS_CHANGE)
                .build();

        Page<AuditEvent> mockPage = new PageImpl<>(List.of(event1, event2));
        when(auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                any(), any(), any())).thenReturn(mockPage);

        // Act
        Page<AuditEvent> result = auditEventService.getAuditTimeline(
                AuditEvent.EntityType.ACCOUNT,
                1L,
                0,
                20);

        // Assert
        assertEquals(2, result.getContent().size());
        assertEquals("1", result.getContent().get(0).getId());
        assertEquals("2", result.getContent().get(1).getId());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findByEntityTypeAndEntityIdOrderByTimestampDesc(
                eq(AuditEvent.EntityType.ACCOUNT),
                eq(1L),
                pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(20, capturedPageable.getPageSize());
    }

    @Test
    void testGetAuditTimeline_EmptyResult() {
        // Arrange
        Page<AuditEvent> emptyPage = new PageImpl<>(List.of());
        when(auditEventRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                any(), any(), any())).thenReturn(emptyPage);

        // Act
        Page<AuditEvent> result = auditEventService.getAuditTimeline(
                AuditEvent.EntityType.CASE,
                999L,
                0,
                20);

        // Assert
        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }
}
