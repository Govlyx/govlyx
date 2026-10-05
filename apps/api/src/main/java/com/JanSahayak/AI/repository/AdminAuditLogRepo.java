package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditLogRepo extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);
    Page<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);
}
