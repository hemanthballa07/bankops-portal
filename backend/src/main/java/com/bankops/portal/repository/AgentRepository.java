package com.bankops.portal.repository;

import com.bankops.portal.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {

    /**
     * Find all active agents
     */
    List<Agent> findByActiveTrue();

    /**
     * Find agent by email
     */
    Agent findByEmail(String email);
}
