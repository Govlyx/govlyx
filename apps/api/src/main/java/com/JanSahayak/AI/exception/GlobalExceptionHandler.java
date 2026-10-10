package com.JanSahayak.AI.exception;

import com.JanSahayak.AI.exception.ToastMessages;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.multipart.MultipartException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // =========================================================================
    // 405 — METHOD NOT ALLOWED
    // =========================================================================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Method not supported [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(
                "Method Not Allowed",
                "The requested HTTP method is not supported for this endpoint: " + ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage("Method not allowed. Please check the request.");

        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    // =========================================================================
    // 404 — NOT FOUND
    // =========================================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Resource not found [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Resource not found", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.NOT_FOUND);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserNotFound(
            UserNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("User not found error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("User not found", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.NOT_FOUND);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handlePostNotFound(
            PostNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Post not found error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Post not found", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.NOT_FOUND);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleCommentNotFound(
            CommentNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Comment not found error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Comment not found", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.NOT_FOUND);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleChatSessionNotFound(
            ChatSessionNotFoundException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Chat session not found error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Chat session not found", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.NOT_FOUND);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles NoSuchElementException thrown by CommunityService (and any other service)
     * when a requested entity is not found via repo.findById().orElseThrow().
     * Placed BEFORE RuntimeException handler — NoSuchElementException extends RuntimeException.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoSuchElement(
            NoSuchElementException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Resource not found [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Resource not found", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.NOT_FOUND);

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // 400 — BAD REQUEST / VALIDATION
    // =========================================================================

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomValidationException(
            ValidationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Custom validation error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Validation failed", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.BAD_REQUEST);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleJakartaValidation(
            jakarta.validation.ValidationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Validation error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Validation failed", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.BAD_REQUEST);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Constraint violation error [{}]: {}", requestId, ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .toastMessage(ToastMessages.BAD_REQUEST)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Method argument not valid error [{}]: {}", requestId, ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName    = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .toastMessage(ToastMessages.BAD_REQUEST)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Illegal argument error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Invalid request", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.BAD_REQUEST);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaValidation(
            MediaValidationException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Media validation error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Media validation failed", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.BAD_REQUEST);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipartException(
            MultipartException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Multipart parsing error [{}]: {}", requestId, ex.getMessage(), ex);

        String userMessage;
        if (ex.getCause() instanceof IllegalStateException) {
            userMessage = "The uploaded file could not be processed. Please try again with a different file.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("size")) {
            userMessage = "The uploaded file is too large. Maximum size allowed is 512MB.";
        } else if (ex.getMessage() != null && ex.getMessage().contains("parse")) {
            userMessage = "There was an error processing your file upload. Please check the file format and try again.";
        } else {
            userMessage = "File upload failed. Please try again with a valid file.";
        }

        ApiResponse<Object> response = ApiResponse.error("File upload failed", userMessage);
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.BAD_REQUEST);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // 403 — FORBIDDEN
    // =========================================================================

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(
            SecurityException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Security error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Access denied", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.FORBIDDEN);

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Access denied error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Access denied",
                "You don't have permission to access this resource");
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.FORBIDDEN);

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // 451 — UNAVAILABLE FOR LEGAL REASONS
    // =========================================================================

    @ExceptionHandler(ContentTakenDownException.class)
    public ResponseEntity<ApiResponse<Object>> handleContentTakenDown(
            ContentTakenDownException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Content taken down [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Unavailable For Legal Reasons", ex.getMessage());
        response.setRequestId(requestId);
        
        // 451 is not in standard HttpStatus enum in older Spring versions, but UNAVAILABLE_FOR_LEGAL_REASONS is available in newer ones.
        // Using raw value 451 to ensure compatibility.
        return ResponseEntity.status(451).body(response);
    }

    // =========================================================================
    // 409 — CONFLICT
    // =========================================================================

    @ExceptionHandler(DuplicatePostException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicatePost(
            DuplicatePostException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Duplicate post conflict [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message(ex.getMessage())
                .data(ex.getDuplicatePost())
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .toastMessage(ToastMessages.CONFLICT)
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Handles IllegalStateException thrown by CommunityService (and any other service)
     * for conflict conditions — e.g. trying to join an archived community, duplicate
     * state transitions, or invariant violations.
     * Placed BEFORE RuntimeException handler — IllegalStateException extends RuntimeException.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Conflict / illegal state [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Conflict", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.CONFLICT);

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // =========================================================================
    // 413 — PAYLOAD TOO LARGE
    // =========================================================================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Max upload size exceeded error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("File too large",
                "The uploaded file exceeds the maximum allowed size");
        response.setRequestId(requestId);

        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // =========================================================================
    // 500 — SERVER ERRORS
    // IMPORTANT: RuntimeException and Exception MUST remain last.
    // All specific subclass handlers above must appear before these two.
    // =========================================================================

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleServiceException(
            ServiceException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Service error [{}]: {}", requestId, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Service error", ex.getMessage());
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.INTERNAL_SERVER_ERROR);

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Runtime error [{}]: {}", requestId, ex.getMessage(), ex);

        String message = ex.getMessage();
        if (message == null || message.isEmpty()) {
            message = "An unexpected error occurred";
        }

        ApiResponse<Object> response = ApiResponse.error("Internal server error", message);
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.INTERNAL_SERVER_ERROR);

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({org.springframework.web.servlet.NoHandlerFoundException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<String>> handleNotFoundException(Exception e, WebRequest request) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.error("Resource not found", e.getMessage(), ToastMessages.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, WebRequest request) {
        String requestId = generateRequestId();
        log.error("Unexpected error [{}]: {}", requestId, ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error("Internal server error",
                "An unexpected error occurred");
        response.setRequestId(requestId);
        response.setToastMessage(ToastMessages.INTERNAL_SERVER_ERROR);

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
