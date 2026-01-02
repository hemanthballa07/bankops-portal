package com.bankops.portal.service;

import com.bankops.portal.entity.FeatureFlag;
import com.bankops.portal.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    /**
     * Checks if a feature flag is enabled.
     * Returns false if the flag doesn't exist.
     */
    public boolean isEnabled(String key) {
        return featureFlagRepository.findByKey(key)
                .map(FeatureFlag::getEnabled)
                .orElse(false);
    }

    /**
     * Sets the enabled state of a feature flag.
     * Creates the flag if it doesn't exist.
     */
    @Transactional
    public void setEnabled(String key, boolean enabled) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElse(FeatureFlag.builder().key(key).build());

        flag.setEnabled(enabled);
        featureFlagRepository.save(flag);
    }
}
