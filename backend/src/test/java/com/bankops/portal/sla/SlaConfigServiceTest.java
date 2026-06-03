package com.bankops.portal.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bankops.portal.dto.SlaConfigDto;
import com.bankops.portal.entity.SlaConfig;
import com.bankops.portal.repository.SlaConfigRepository;

class SlaConfigServiceTest {

    private SlaConfigRepository repository;
    private SlaConfigService service;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        repository = mock(SlaConfigRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault());
        service = new SlaConfigService(repository, fixedClock);
    }

    @Test
    void getDuration_noRow_fallsBackToEnumDefault() {
        when(repository.findById("P1")).thenReturn(Optional.empty());
        assertThat(service.getDuration(SlaPriority.P1)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void getDuration_withRow_returnsOverride() {
        when(repository.findById("P1")).thenReturn(Optional.of(
                SlaConfig.builder().priority("P1").durationSeconds(3600).build()));
        assertThat(service.getDuration(SlaPriority.P1)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void update_persistsOverrideAndStampsUpdatedAt() {
        when(repository.findById("P2")).thenReturn(Optional.empty());
        when(repository.save(any(SlaConfig.class))).thenAnswer(i -> i.getArgument(0));

        SlaConfigDto dto = service.update("P2", 7200);

        assertThat(dto.getPriority()).isEqualTo("P2");
        assertThat(dto.getDurationSeconds()).isEqualTo(7200);
        assertThat(dto.getUpdatedAt()).isEqualTo(java.time.LocalDateTime.now(fixedClock));
    }

    @Test
    void update_rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> service.update("P1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_rejectsUnknownPriority() {
        assertThatThrownBy(() -> service.update("P9", 3600))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_returnsAllThreePriorities_withEffectiveDurations() {
        when(repository.findById("P1")).thenReturn(Optional.of(
                SlaConfig.builder().priority("P1").durationSeconds(3600).build()));
        when(repository.findById("P2")).thenReturn(Optional.empty());
        when(repository.findById("P3")).thenReturn(Optional.empty());

        var list = service.list();

        assertThat(list).hasSize(3);
        assertThat(list).anySatisfy(d -> {
            assertThat(d.getPriority()).isEqualTo("P1");
            assertThat(d.getDurationSeconds()).isEqualTo(3600); // override
        });
        assertThat(list).anySatisfy(d -> {
            assertThat(d.getPriority()).isEqualTo("P2");
            assertThat(d.getDurationSeconds()).isEqualTo(Duration.ofHours(72).getSeconds()); // enum default
        });
    }
}
