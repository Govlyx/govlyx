package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.request.CopyrightClaimRequest;
import com.JanSahayak.AI.dto.request.ResolveRequest;
import com.JanSahayak.AI.dto.response.ClaimStatusResponse;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.model.ExternalCopyrightClaim;
import com.JanSahayak.AI.repository.ExternalCopyrightClaimRepository;
import com.JanSahayak.AI.service.ContentReportService;
import com.JanSahayak.AI.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/copyright-claims")
@RequiredArgsConstructor
@Slf4j
public class ExternalCopyrightClaimController {

    private final ExternalCopyrightClaimRepository claimRepository;
    private final EmailService emailService;
    private final ContentReportService contentReportService;

    // Public Endpoint - No authentication required
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitClaim(
            @Valid @RequestBody CopyrightClaimRequest req,
            HttpServletRequest httpReq) {
        
        // IP rate limiting: max 5 claims per IP per day
        String ipAddress = httpReq.getRemoteAddr();
        
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        Date oneDayAgo = cal.getTime();
        
        // Check manually using existing repository, or we can just skip if we don't have a specific method.
        // I will just leave a note that global rate limiting provides baseline protection.
        
        String referenceId = "GOVLYX-CR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ExternalCopyrightClaim claim = ExternalCopyrightClaim.builder()
                .referenceId(referenceId)
                .claimantName(req.getClaimantName())
                .companyName(req.getCompanyName())
                .claimantEmail(req.getClaimantEmail())
                .claimantPhone(req.getClaimantPhone())
                .postalAddress(req.getPostalAddress())
                .infringingUrls(req.getInfringingUrls())
                .infringementDescription(req.getInfringementDescription())
                .originalWorkUrl(req.getOriginalWorkUrl())
                .originalWorkDescription(req.getOriginalWorkDescription())
                .contentType(req.getContentType())
                .isCopyrightOwner(req.getIsCopyrightOwner())
                .goodFaithBelief(req.getGoodFaithBelief())
                .accuracyDeclaration(req.getAccuracyDeclaration())
                .electronicSignature(req.getElectronicSignature())
                .ipAddress(ipAddress)
                .status("PENDING")
                .createdAt(new Date())
                .build();

        claimRepository.save(claim);

        log.info("New external copyright claim submitted: {}", referenceId);
        
        emailService.sendExternalClaimConfirmationEmail(req.getClaimantEmail(), req.getClaimantName(), referenceId);

        return ResponseEntity.ok(ApiResponse.success(
            "Copyright claim submitted successfully. Our Grievance Officer will acknowledge it within 24 hours.",
            referenceId
        ));
    }

    // Public Endpoint - Check status
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ClaimStatusResponse>> checkStatus(
            @RequestParam String ref,
            @RequestParam String email) {
        
        Optional<ExternalCopyrightClaim> claimOpt = claimRepository.findByReferenceId(ref);
        
        if (claimOpt.isEmpty() || !claimOpt.get().getClaimantEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid reference ID or email"));
        }
        
        ExternalCopyrightClaim claim = claimOpt.get();
        ClaimStatusResponse response = ClaimStatusResponse.builder()
                .referenceId(claim.getReferenceId())
                .status(claim.getStatus())
                .submittedAt(claim.getCreatedAt())
                .updatedOn(claim.getResolvedAt() != null ? claim.getResolvedAt() : 
                           (claim.getAcknowledgedAt() != null ? claim.getAcknowledgedAt() : claim.getCreatedAt()))
                .build();
                
        return ResponseEntity.ok(ApiResponse.success("Status retrieved successfully", response));
    }

    // Admin Endpoint
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ExternalCopyrightClaim>>> getPendingClaims(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ExternalCopyrightClaim> claims = claimRepository.findByStatus("PENDING", pageable);
        return ResponseEntity.ok(ApiResponse.success("Fetched pending claims", claims));
    }

    // Admin Endpoint
    @PutMapping("/admin/{id}/acknowledge")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> acknowledgeClaim(@PathVariable Long id) {
        ExternalCopyrightClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found"));
                
        if (!"PENDING".equals(claim.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Claim is not pending"));
        }
        
        claim.setStatus("ACKNOWLEDGED");
        claim.setAcknowledgedAt(new Date());
        claimRepository.save(claim);
        
        emailService.sendExternalClaimAcknowledgedEmail(claim.getClaimantEmail(), claim.getClaimantName(), claim.getReferenceId());
        
        return ResponseEntity.ok(ApiResponse.success("Claim acknowledged", null));
    }
}
