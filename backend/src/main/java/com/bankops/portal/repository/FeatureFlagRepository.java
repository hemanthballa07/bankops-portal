package com.bankops.portal.repository;

import com.bankops.portal.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {

    Optional<FeatureFlag> findByKey(String key);
}
