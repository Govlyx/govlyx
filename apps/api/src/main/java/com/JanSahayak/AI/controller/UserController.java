package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.PaginatedResponse;
import com.JanSahayak.AI.dto.UserMeResponse;
import com.JanSahayak.AI.dto.UserTagSuggestionDto;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.UserNotFoundException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.service.CloudinaryStorageService;
import com.JanSahayak.AI.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserRepo    userRepository;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final com.JanSahayak.AI.service.PincodeValidationService pincodeValidationService;
    private final com.JanSahayak.AI.service.EmailService emailService;
    private final com.JanSahayak.AI.service.PinCodeLookupService pinCodeLookupService;
    // ===== User Lookup Methods =====

    /**
     * Get the currently authenticated user's own profile.
     *
     * @Transactional(readOnly=true) keeps the Hibernate session open through Jackson
     * serialization, preventing LazyInitializationException on lazy collections.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserMeResponse>> getCurrentUserProfile() {
        try {
            User user = getCurrentUser();
            return ResponseEntity.ok(ApiResponse.success("Current user retrieved successfully", new UserMeResponse(user)));

        } catch (ValidationException e) {
            log.warn("Validation error in getCurrentUserProfile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.error("Unauthorized", e.getMessage(), com.JanSahayak.AI.exception.ToastMessages.UNAUTHORIZED));
        } catch (Exception e) {
            log.error("Unexpected error in getCurrentUserProfile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving current user"));
        }
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<User>> findByUsername(
            @PathVariable @Size(min = 4, max = 100, message = "Username must be between 4 and 100 characters") String username) {

        try {
            User user = userService.findByUsername(username);
            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));

        } catch (UserNotFoundException e) {
            log.warn("User not found by username: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            log.warn("Validation error in findByUsername: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findByUsername for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user"));
        }
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<User>> findById(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId) {

        try {
            User user = userService.findById(userId);
            return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));

        } catch (UserNotFoundException e) {
            log.warn("User not found by ID: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            log.warn("Validation error in findById: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findById for userId: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user"));
        }
    }

    // ===== Department User Search Methods =====

    @GetMapping("/departments/by-pincode/{pincode}")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> findDepartmentUsersByPincode(
            @PathVariable @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format") String pincode,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.findDepartmentUsersByPincode(pincode, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success(
                    "Department users by pincode retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in findDepartmentUsersByPincode: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in findDepartmentUsersByPincode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findDepartmentUsersByPincode for pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving department users"));
        }
    }

    @GetMapping("/departments/by-state/{state}")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> findDepartmentUsersByState(
            @PathVariable @Size(min = 2, max = 50, message = "State name must be between 2 and 50 characters") String state,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.findDepartmentUsersByState(state, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success(
                    "Department users by state retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in findDepartmentUsersByState: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in findDepartmentUsersByState: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findDepartmentUsersByState for state: {}", state, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving department users"));
        }
    }

    @GetMapping("/departments/by-district")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> findDepartmentUsersByDistrict(
            @RequestParam @Size(min = 2, max = 50, message = "State name must be between 2 and 50 characters") String state,
            @RequestParam @Size(min = 2, max = 50, message = "District name must be between 2 and 50 characters") String district,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.findDepartmentUsersByDistrict(state, district, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success(
                    "Department users by district retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in findDepartmentUsersByDistrict: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in findDepartmentUsersByDistrict: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in findDepartmentUsersByDistrict for state: {} district: {}", state, district, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving department users"));
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<UserTagSuggestionDto>>> searchUsers(
            @RequestParam @Size(min = 2, max = 50, message = "Query must be between 2 and 50 characters") String query,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<UserTagSuggestionDto> response = userService.searchUsers(query, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in searchUsers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in searchUsers for query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while searching users"));
        }
    }

    // ===== User Search and Tagging Methods =====

    @GetMapping("/search/tagging")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<UserTagSuggestionDto>>> searchUsersForTagging(
            @RequestParam @Size(min = 2, max = 50, message = "Query must be between 2 and 50 characters") String query,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<UserTagSuggestionDto> response = userService.searchUsersForTagging(query, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Users for tagging retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in searchUsersForTagging: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in searchUsersForTagging: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in searchUsersForTagging for query: {}", query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while searching users for tagging"));
        }
    }

    // ===== Geographic Distribution Methods =====

    @GetMapping("/distribution/by-pincode")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserDistributionByPincode() {
        try {
            Map<String, Long> distribution = userService.getUserDistributionByPincode();
            return ResponseEntity.ok(ApiResponse.success(
                    "User distribution by pincode retrieved successfully", distribution));

        } catch (ServiceException e) {
            log.error("Service error in getUserDistributionByPincode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getUserDistributionByPincode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user distribution by pincode"));
        }
    }

    @GetMapping("/distribution/by-state")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserDistributionByState() {
        try {
            Map<String, Long> distribution = userService.getUserDistributionByState();
            return ResponseEntity.ok(ApiResponse.success(
                    "User distribution by state retrieved successfully", distribution));

        } catch (ServiceException e) {
            log.error("Service error in getUserDistributionByState: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getUserDistributionByState", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving user distribution by state"));
        }
    }

    // ===== Permission Check Methods =====

    @GetMapping("/permissions/resolve-posts/{pincode}")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> canUserResolvePostsInPincode(
            @PathVariable @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format") String pincode) {

        try {
            User currentUser = getCurrentUser();
            boolean canResolve = userService.canUserResolvePostsInPincode(currentUser, pincode);
            return ResponseEntity.ok(ApiResponse.success("Permission check completed successfully", canResolve));

        } catch (ValidationException e) {
            log.warn("Validation error in canUserResolvePostsInPincode: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in canUserResolvePostsInPincode: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in canUserResolvePostsInPincode for pincode: {}", pincode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while checking permissions"));
        }
    }

    // ===== User Update Methods =====

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateUserProfile(@Valid @RequestBody UserUpdateRequest updateRequest) {
        try {
            User currentUser = getCurrentUser();

            User userToUpdate = User.builder()
                    .id(currentUser.getId())
                    .email(updateRequest.getEmail())
                    .bio(updateRequest.getBio())
                    .pincode(updateRequest.getPincode())
                    .preferredLanguage(updateRequest.getPreferredLanguage())
                    .interfaceLanguage(updateRequest.getInterfaceLanguage())
                    .autoTranslate(updateRequest.getAutoTranslate())
                    .profanityFilterLevel(updateRequest.getProfanityFilterLevel())
                    .mutedWords(updateRequest.getMutedWords())
                    .build();

            String originalEmail = currentUser.getEmail();
            User updatedUser = userService.updateUser(userToUpdate);
            
            if (updatedUser.getPendingEmail() != null && updatedUser.getEmailUpdateToken() != null && !updatedUser.getPendingEmail().equals(originalEmail)) {
                emailService.sendEmailUpdateAlertEmail(updatedUser);
                emailService.sendEmailUpdateVerificationEmail(updatedUser, updatedUser.getEmailUpdateToken(), updatedUser.getPendingEmail());
                return ResponseEntity.ok(ApiResponse.success("Verification link sent to new email address. Please check your inbox.", new UserMeResponse(updatedUser)));
            }
            
            return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", new UserMeResponse(updatedUser)));

        } catch (ValidationException e) {
            log.warn("Validation error in updateUserProfile: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in updateUserProfile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in updateUserProfile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while updating user profile"));
        }
    }

    @PutMapping("/change-password")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            User currentUser = getCurrentUser();
            userService.changePassword(currentUser.getId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));

        } catch (ValidationException e) {
            log.warn("Validation error in changePassword: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in changePassword: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in changePassword", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while changing password"));
        }
    }

    @PutMapping("/update-pincode")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updatePincode(@Valid @RequestBody PincodeUpdateRequest request) {
        try {
            User currentUser = getCurrentUser();
            
            try {
                if (!pincodeValidationService.isValidIndianPincode(request.getPincode())) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid Indian Pincode. Please enter a valid pincode."));
                }
            } catch (com.JanSahayak.AI.service.PincodeValidationService.ApiUnavailableException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    ApiResponse.error("Pincode verification service is temporarily down. Please try again later."));
            }

            userService.updatePincode(currentUser, request.getPincode());

            return ResponseEntity.ok(ApiResponse.success("Pincode updated successfully", null));
        } catch (ValidationException e) {
            log.warn("Validation error in updatePincode: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in updatePincode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while updating pincode"));
        }
    }

    // ===== Profile Image Upload =====

    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadProfileImage(@RequestParam("file") MultipartFile file) {
        try {
            User currentUser = getCurrentUser();

            // Validate file
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No file provided"));
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Only image files are allowed"));
            }

            long maxSize = 5 * 1024 * 1024; // 5 MB
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Image must be under 5 MB"));
            }

            // Delete old profile image from Cloudinary if exists
            if (currentUser.getProfileImage() != null && !currentUser.getProfileImage().isBlank()) {
                try {
                    cloudinaryStorageService.deleteFile(currentUser.getProfileImage());
                } catch (Exception e) {
                    log.warn("Failed to delete old profile image for user {}: {}", currentUser.getId(), e.getMessage());
                }
            }

            // Upload new image
            String imageUrl = cloudinaryStorageService.uploadFile(file, currentUser.getId(), "posts");

            try {
                // Save URL to user via service layer to respect transaction boundaries
                userService.updateProfileImage(currentUser, imageUrl);
            } catch (Exception e) {
                // If DB save fails, clean up Cloudinary to prevent orphaned files
                try {
                    cloudinaryStorageService.deleteFile(imageUrl);
                } catch (Exception ex) {
                    log.error("Failed to delete orphaned Cloudinary image after DB failure: {}", imageUrl, ex);
                }
                throw e; // Let the outer catch blocks or global handler catch it
            }

            log.info("Profile image updated for user: {} (ID: {})", currentUser.getActualUsername(), currentUser.getId());
            return ResponseEntity.ok(ApiResponse.success("Profile image updated successfully", imageUrl));

        } catch (ServiceException e) {
            log.error("Service error uploading profile image: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Failed to upload image", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error uploading profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while uploading profile image"));
        }
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOwnAccount() {
        try {
            User currentUser = getCurrentUser();
            userService.deactivateUser(currentUser.getId(), currentUser);
            log.info("User {} (ID: {}) deactivated their own account", currentUser.getUsername(), currentUser.getId());
            return ResponseEntity.ok(ApiResponse.success("Account deactivated successfully", null));

        } catch (ValidationException e) {
            log.warn("Validation error in deleteOwnAccount: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Deactivation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in deleteOwnAccount: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in deleteOwnAccount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while deactivating account"));
        }
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @PathVariable @Min(value = 1, message = "User ID must be positive") Long userId,
            @Valid @RequestBody UserUpdateRequest updateRequest) {

        try {
            User userToUpdate = User.builder()
                    .id(userId)
                    .email(updateRequest.getEmail())
                    .bio(updateRequest.getBio())
                    .pincode(updateRequest.getPincode())
                    .build();

            User updatedUser = userService.updateUser(userToUpdate);
            return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));

        } catch (UserNotFoundException e) {
            log.warn("User not found for update: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("User not found", e.getMessage()));
        } catch (ValidationException e) {
            log.warn("Validation error in updateUser: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in updateUser: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in updateUser for userId: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while updating user"));
        }
    }

    // ===== User Listing Methods =====

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getAllActiveUsers(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.getAllActiveUsers(beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Active users retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getAllActiveUsers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getAllActiveUsers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getAllActiveUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving active users"));
        }
    }

    @GetMapping("/by-role/{roleName}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getUsersByRole(
            @PathVariable @Size(min = 3, max = 20, message = "Role name must be between 3 and 20 characters") String roleName,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.getUsersByRole(roleName, beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Users by role retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getUsersByRole: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getUsersByRole: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getUsersByRole for role: {}", roleName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving users by role"));
        }
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<User>>> getRecentlyCreatedUsers(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) @Min(value = 1, message = "Limit must be at least 1") Integer limit) {

        try {
            PaginatedResponse<User> response = userService.getRecentlyCreatedUsers(beforeId, limit);
            return ResponseEntity.ok(ApiResponse.success("Recently created users retrieved successfully", response));

        } catch (ValidationException e) {
            log.warn("Validation error in getRecentlyCreatedUsers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in getRecentlyCreatedUsers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in getRecentlyCreatedUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while retrieving recently created users"));
        }
    }

    // ===== Helper Methods =====

    /**
     * FIX: Changed from userRepository.findByEmail() to userRepository.findByEmailWithRole().
     *
     * User.role is now FetchType.LAZY. The original findByEmail() returned a user with
     * an uninitialized role proxy — any subsequent call to user.isAdmin() or
     * user.isDepartment() (inside security checks or service calls) would trigger a
     * second SELECT to load the role.
     *
     * findByEmailWithRole() uses JOIN FETCH to load both User and Role in ONE query,
     * so the controller has the fully-hydrated user ready for any permission check
     * without an extra DB round-trip.
     */
    private User getCurrentUser() {
        return userService.getUserFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    }

    public static class UserUpdateRequest {
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;

        @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
        private String bio;

        @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format")
        private String pincode;

        @Size(max = 10, message = "Preferred language cannot exceed 10 characters")
        private String preferredLanguage;

        @Size(max = 10, message = "Interface language cannot exceed 10 characters")
        private String interfaceLanguage;

        private Boolean autoTranslate;

        @Size(max = 20, message = "Profanity filter level cannot exceed 20 characters")
        private String profanityFilterLevel;

        @Size(max = 1000, message = "Muted words cannot exceed 1000 characters")
        private String mutedWords;

        public String getEmail()   { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getBio()     { return bio; }
        public void setBio(String bio) { this.bio = bio; }

        public String getPincode() { return pincode; }
        public void setPincode(String pincode) { this.pincode = pincode; }

        public String getPreferredLanguage() { return preferredLanguage; }
        public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

        public String getInterfaceLanguage() { return interfaceLanguage; }
        public void setInterfaceLanguage(String interfaceLanguage) { this.interfaceLanguage = interfaceLanguage; }

        public Boolean getAutoTranslate() { return autoTranslate; }
        public void setAutoTranslate(Boolean autoTranslate) { this.autoTranslate = autoTranslate; }

        public String getProfanityFilterLevel() { return profanityFilterLevel; }
        public void setProfanityFilterLevel(String profanityFilterLevel) { this.profanityFilterLevel = profanityFilterLevel; }

        public String getMutedWords() { return mutedWords; }
        public void setMutedWords(String mutedWords) { this.mutedWords = mutedWords; }
    }

    public static class ChangePasswordRequest {
        @jakarta.validation.constraints.NotEmpty(message = "Old password is required")
        private String oldPassword;

        @jakarta.validation.constraints.Size(min = 8, message = "New password must be at least 8 characters long")
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
    
    public static class PincodeUpdateRequest {
        @jakarta.validation.constraints.NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9]\\d{5}$", message = "Invalid Indian pincode format")
        private String pincode;

        public String getPincode() { return pincode; }
        public void setPincode(String pincode) { this.pincode = pincode; }
    }



    @PatchMapping("/settings/theme")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateTheme(@Valid @RequestBody ThemeUpdateRequest request) {
        try {
            User currentUser = getCurrentUser();
            userService.updateTheme(currentUser.getId(), request.getTheme());
            return ResponseEntity.ok(ApiResponse.success("Theme updated successfully", null));
        } catch (ServiceException e) {
            log.error("Service error in updateTheme: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in updateTheme", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred while updating theme"));
        }
    }

    public static class ThemeUpdateRequest {
        @NotBlank(message = "Theme is required")
        @Pattern(regexp = "^(?i)(light|dark)$", message = "Theme must be either 'light' or 'dark'")
        @Size(max = 20, message = "Theme cannot exceed 20 characters")
        private String theme;

        public String getTheme() { return theme != null ? theme.toLowerCase().trim() : null; }
        public void setTheme(String theme) { this.theme = theme; }
    }

    // ===== GPS Location Endpoint =====

    /**
     * Save or update the user's home GPS coordinates.
     * PUT /api/users/location
     *
     * Called once by the frontend after the user grants browser location permission.
     * Persists the device-resolved latitude/longitude on the User entity, enabling
     * the NEARBY (5km radius) Neighborhood Q&A feed scope.
     *
     * Coordinates are NEVER returned to other clients (JsonIgnore on homeLatitude/
     * homeLongitude in User.java prevents stalking). They are backend-only for math.
     */
    @PutMapping("/location")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateHomeLocation(
            @Valid @RequestBody LocationUpdateRequest request) {
        try {
            User currentUser = getCurrentUser();

            // Validate coordinate ranges
            if (request.getLatitude() == null || request.getLongitude() == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Latitude and longitude are required"));
            }
            double lat = request.getLatitude().doubleValue();
            double lng = request.getLongitude().doubleValue();
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid coordinates: latitude must be ±90 and longitude ±180"));
            }

            currentUser.setHomeLatitude(request.getLatitude());
            currentUser.setHomeLongitude(request.getLongitude());
            
            // Sync pincode based on new GPS location
            if (request.getPincode() != null && !request.getPincode().trim().isEmpty()) {
                // If the frontend/device resolved the pincode via its own geocoding API, use it directly
                currentUser.setPincode(request.getPincode());
                log.info("[GPS] Auto-synced pincode {} for user {} based on device-provided pincode", request.getPincode(), currentUser.getId());
            } else {
                // Fallback: calculate nearest pincode on the backend
                pinCodeLookupService.findClosestPincode(lat, lng).ifPresent(p -> {
                    currentUser.setPincode(p.getPincode());
                    log.info("[GPS] Auto-synced pincode {} for user {} based on backend GPS calculation", p.getPincode(), currentUser.getId());
                });
            }

            User savedUser = userRepository.save(currentUser);

            log.info("[GPS] Saved home location for user {} lat={} lng={}",
                    savedUser.getId(), lat, lng);

            return ResponseEntity.ok(ApiResponse.success("Home location saved", new UserMeResponse(savedUser)));

        } catch (Exception e) {
            log.error("Failed to update home location", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to save location: " + e.getMessage()));
        }
    }

    public static class LocationUpdateRequest {
        @jakarta.validation.constraints.NotNull(message = "Latitude is required")
        @jakarta.validation.constraints.DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
        @jakarta.validation.constraints.DecimalMax(value = "90.0", message = "Latitude must be <= 90")
        private java.math.BigDecimal latitude;

        @jakarta.validation.constraints.NotNull(message = "Longitude is required")
        @jakarta.validation.constraints.DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
        @jakarta.validation.constraints.DecimalMax(value = "180.0", message = "Longitude must be <= 180")
        private java.math.BigDecimal longitude;

        private String pincode;

        public java.math.BigDecimal getLatitude()  { return latitude; }
        public java.math.BigDecimal getLongitude() { return longitude; }
        public String getPincode() { return pincode; }
        
        public void setLatitude(java.math.BigDecimal latitude)   { this.latitude  = latitude; }
        public void setLongitude(java.math.BigDecimal longitude) { this.longitude = longitude; }
        public void setPincode(String pincode) { this.pincode = pincode; }
    }
}
