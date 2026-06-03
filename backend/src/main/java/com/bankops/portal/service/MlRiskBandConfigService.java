package com.bankops.portal.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bankops.portal.dto.MlRiskBandConfigDto;
import com.bankops.portal.entity.MlRiskBandConfig;
import com.bankops.portal.repository.MlRiskBandConfigRepository;

import lombok.RequiredArgsConstructor;

/**
 * Runtime-configurable Low/Med/High thresholds for the advisory ML risk chip. A singleton override
 * row wins; otherwise the defaults apply (so the chip's banding is unchanged until an admin edits).
 */
@Service
@RequiredArgsConstructor
public class MlRiskBandConfigService {

    static final String CONFIG_ID = "default";
    static final double DEFAULT_MED = 0.40;
    static final double DEFAULT_HIGH = 0.70;

    private final MlRiskBandConfigRepository repository;
    private final Clock clock;

    public MlRiskBandConfigDto getConfig() {
        return repository.findById(CONFIG_ID)
                .map(c -> MlRiskBandConfigDto.builder()
                        .medThreshold(c.getMedThreshold())
                        .highThreshold(c.getHighThreshold())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .orElse(MlRiskBandConfigDto.builder()
                        .medThreshold(DEFAULT_MED)
                        .highThreshold(DEFAULT_HIGH)
                        .updatedAt(null)
                        .build());
    }

    @Transactional
    public MlRiskBandConfigDto update(double medThreshold, double highThreshold) {
        if (medThreshold <= 0 || highThreshold >= 1 || medThreshold >= highThreshold) {
            throw new IllegalArgumentException("Require 0 < medThreshold < highThreshold < 1");
        }
        MlRiskBandConfig row = repository.findById(CONFIG_ID)
                .orElse(MlRiskBandConfig.builder().id(CONFIG_ID).build());
        row.setMedThreshold(medThreshold);
        row.setHighThreshold(highThreshold);
        row.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(row);
        return MlRiskBandConfigDto.builder()
                .medThreshold(medThreshold)
                .highThreshold(highThreshold)
                .updatedAt(row.getUpdatedAt())
                .build();
    }
}
