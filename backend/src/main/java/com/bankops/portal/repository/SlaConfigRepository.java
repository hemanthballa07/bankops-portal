package com.bankops.portal.repository;

import com.bankops.portal.entity.SlaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SlaConfigRepository extends JpaRepository<SlaConfig, String> {
}
