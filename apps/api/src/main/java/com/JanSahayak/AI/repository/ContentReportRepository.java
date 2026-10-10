package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.enums.ReportCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    // ── Check if this user already reported this piece of content ──────────────
    boolean existsByReporter_IdAndTargetTypeAndTargetId(
            Long reporterId, String targetType, Long targetId);

    // ── Count how many PENDING reports exist for a piece of content ───────────
    long countByTargetTypeAndTargetIdAndStatus(
            String targetType, Long targetId, String status);

    // ── Admin: paginated list, all statuses ──────────────────────────────────
    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    Page<ContentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Admin: filter by status ───────────────────────────────────────────────
    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    Page<ContentReport> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    // ── Admin: emergency queue (24h SLA) ─────────────────────────────────────
    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    Page<ContentReport> findByStatusAndIsEmergencyOrderByCreatedAtAsc(
            String status, Boolean isEmergency, Pageable pageable);

    // ── Admin: standard queue (15-day SLA) ───────────────────────────────────
    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    @Query("SELECT r FROM ContentReport r WHERE r.status = :status AND r.isEmergency = false ORDER BY r.createdAt ASC")
    Page<ContentReport> findStandardQueue(@Param("status") String status, Pageable pageable);

    // ── Count helpers for dashboard stats ─────────────────────────────────────
    long countByStatus(String status);
    long countByStatusAndIsEmergency(String status, Boolean isEmergency);

    // ── Find all reports for a specific piece of content (for admin detail) ──
    List<ContentReport> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId);
}
