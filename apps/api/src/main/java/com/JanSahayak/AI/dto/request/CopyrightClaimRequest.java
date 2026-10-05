package com.JanSahayak.AI.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CopyrightClaimRequest {

    @NotBlank(message = "Claimant name is required")
    private String claimantName;

    private String companyName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String claimantEmail;

    private String claimantPhone;

    private String postalAddress;

    @NotBlank(message = "Infringing URLs are required")
    private String infringingUrls;

    @NotBlank(message = "Description of infringement is required")
    private String infringementDescription;

    @NotBlank(message = "Original work URL is required")
    private String originalWorkUrl;

    @NotBlank(message = "Description of original work is required")
    private String originalWorkDescription;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotNull(message = "Copyright owner declaration is required")
    private Boolean isCopyrightOwner;

    @NotNull(message = "Good faith belief declaration is required")
    private Boolean goodFaithBelief;

    @NotNull(message = "Accuracy declaration is required")
    private Boolean accuracyDeclaration;

    @NotBlank(message = "Electronic signature is required")
    private String electronicSignature;
}
