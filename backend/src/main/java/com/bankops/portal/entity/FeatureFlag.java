package com.bankops.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feature_flags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {

    @Id
    @Column(name = "flag_key", unique = true, nullable = false, length = 100)
    private String key;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;
}
