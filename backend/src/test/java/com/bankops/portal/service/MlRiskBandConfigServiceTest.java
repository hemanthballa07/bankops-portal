package com.bankops.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bankops.portal.dto.MlRiskBandConfigDto;
import com.bankops.portal.entity.MlRiskBandConfig;
import com.bankops.portal.repository.MlRiskBandConfigRepository;

@ExtendWith(MockitoExtension.class)
class MlRiskBandConfigServiceTest {

    @Mock
    private MlRiskBandConfigRepository repository;
    private MlRiskBandConfigService service;

    @BeforeEach
    void setUp() {
        service = new MlRiskBandConfigService(repository,
                Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void getConfig_returnsDefaults_whenNoRow() {
        when(repository.findById("default")).thenReturn(Optional.empty());
        MlRiskBandConfigDto dto = service.getConfig();
        assertThat(dto.getMedThreshold()).isEqualTo(0.40);
        assertThat(dto.getHighThreshold()).isEqualTo(0.70);
        assertThat(dto.getUpdatedAt()).isNull();
    }

    @Test
    void getConfig_returnsOverride_whenRowPresent() {
        when(repository.findById("default")).thenReturn(Optional.of(MlRiskBandConfig.builder()
                .id("default").medThreshold(0.3).highThreshold(0.6)
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build()));
        MlRiskBandConfigDto dto = service.getConfig();
        assertThat(dto.getMedThreshold()).isEqualTo(0.3);
        assertThat(dto.getHighThreshold()).isEqualTo(0.6);
        assertThat(dto.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_upsertsSingletonAndStampsUpdatedAt() {
        when(repository.findById("default")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MlRiskBandConfigDto dto = service.update(0.35, 0.65);
        assertThat(dto.getMedThreshold()).isEqualTo(0.35);
        assertThat(dto.getHighThreshold()).isEqualTo(0.65);
        assertThat(dto.getUpdatedAt()).isNotNull();
        ArgumentCaptor<MlRiskBandConfig> captor = ArgumentCaptor.forClass(MlRiskBandConfig.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("default");
    }

    @Test
    void update_rejectsMedNotLessThanHigh() {
        assertThatThrownBy(() -> service.update(0.7, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_rejectsMedZeroOrNegative() {
        assertThatThrownBy(() -> service.update(0.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_rejectsHighAtOrAboveOne() {
        assertThatThrownBy(() -> service.update(0.4, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
