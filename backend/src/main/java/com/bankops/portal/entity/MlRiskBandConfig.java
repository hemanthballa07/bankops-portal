package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Singleton runtime override of the ML risk chip's Low/Med/High band thresholds. */
@Entity
@Table(name = "ml_risk_band_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlRiskBandConfig {

    @Id
    @Column(name = "id", nullable = false, length = 20)
    private String id; // singleton key, always "default"

    @Column(name = "med_threshold", nullable = false)
    private double medThreshold;

    @Column(name = "high_threshold", nullable = false)
    private double highThreshold;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
