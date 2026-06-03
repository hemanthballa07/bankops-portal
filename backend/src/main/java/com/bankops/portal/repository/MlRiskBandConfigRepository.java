package com.bankops.portal.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bankops.portal.entity.MlRiskBandConfig;

public interface MlRiskBandConfigRepository extends JpaRepository<MlRiskBandConfig, String> {
}
