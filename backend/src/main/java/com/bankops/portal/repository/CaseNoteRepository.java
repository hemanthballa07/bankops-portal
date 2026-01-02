package com.bankops.portal.repository;

import com.bankops.portal.entity.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {

    List<CaseNote> findBySupportCaseIdOrderByCreatedAtDesc(Long caseId);
}
