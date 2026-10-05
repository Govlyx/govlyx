package com.govlyx.AI.repository;

import com.govlyx.AI.model.ExternalCopyrightClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalCopyrightClaimRepository extends JpaRepository<ExternalCopyrightClaim, Long> {

    Page<ExternalCopyrightClaim> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    Optional<ExternalCopyrightClaim> findByReferenceId(String referenceId);

    Page<ExternalCopyrightClaim> findByClaimantEmail(String email, Pageable pageable);

    java.util.List<ExternalCopyrightClaim> findByStatusAndCreatedAtBefore(String status, java.util.Date date);
}
