package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.enums.ReportCategory;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.model.ContentReport;
import com.JanSahayak.AI.service.ContentReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
@Slf4j
public class ContentReportController {

    @Autowired
    private ContentReportService reportService;

    // ─────────────────────────────────────────────────────────────────────────
    // Request DTO
    // ─────────────────────────────────────────────────────────────────────────

    public static class ReportRequest {
        @NotBlank(message = "Target type is required")
        public String targetType;   // SOCIAL_POST | POST | COMMENT
        
        @NotNull(message = "Target ID is required")
        public Long   targetId;
        
        @NotBlank(message = "Category is required")
        public String category;     // enum name e.g. "HARASSMENT"
        
        public String description;  // optional free-text
    }

    public static class ResolveRequest {
        @NotBlank(message = "Resolution status is required")
        public String resolution;   // RESOLVED_REMOVED | RESOLVED_DISMISSED
        
        public String notes;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/reports  — any authenticated user
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<String>> fileReport(@Valid @RequestBody ReportRequest req) {
        try {
            ReportCategory category = ReportCategory.valueOf(req.category.toUpperCase());
            ContentReport report = reportService.fileReport(
                    req.targetType, req.targetId, category, req.description);

            String msg = Boolean.TRUE.equals(report.getIsEmergency())
                    ? "Report submitted. This has been flagged as an emergency (24-hour review)."
                    : "Report submitted. It will be reviewed within 15 days as per IT Rules 2021.";

            return ResponseEntity.ok(ApiResponse.success(msg));

        } catch (ServiceException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid report category: " + req.category));
        } catch (Exception e) {
            log.error("Failed to file report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to submit report. Please try again."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/reports/admin/stats  — admin only
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        return ResponseEntity.ok(
                ApiResponse.success("Stats fetched", reportService.getDashboardStats()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/reports/admin/emergency?page=0&size=20  — admin only
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/admin/emergency")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ContentReport>>> getEmergencyQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("Emergency queue", reportService.getPendingEmergencyQueue(page, size)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/reports/admin/standard?page=0&size=20  — admin only
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/admin/standard")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ContentReport>>> getStandardQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("Standard queue", reportService.getPendingStandardQueue(page, size)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/reports/admin/all?page=0&size=20  — admin only
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ContentReport>>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("All reports", reportService.getAllReports(page, size)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/reports/admin/{id}/resolve  — admin only
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/admin/{id}/resolve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> resolveReport(
            @PathVariable Long id,
            @Valid @RequestBody ResolveRequest req) {
        try {
            reportService.resolveReport(id, req.resolution, req.notes);
            return ResponseEntity.ok(ApiResponse.success("Report resolved successfully."));
        } catch (ServiceException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to resolve report {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resolve report."));
        }
    }
}
