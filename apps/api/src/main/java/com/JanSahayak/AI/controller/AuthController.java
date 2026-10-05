package com.JanSahayak.AI.controller;

import com.JanSahayak.AI.dto.AuthRequest;
import com.JanSahayak.AI.dto.AuthResponse;
import com.JanSahayak.AI.dto.RegisterRequest;
import com.JanSahayak.AI.dto.GoogleAuthRequest;
import com.JanSahayak.AI.exception.ApiResponse;
import com.JanSahayak.AI.exception.ServiceException;
import com.JanSahayak.AI.exception.ValidationException;
import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.repository.RoleRepo;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.security.JwtUtil;
import com.JanSahayak.AI.security.CustomUserDetailsService;
import com.JanSahayak.AI.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.UUID;
import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.JanSahayak.AI.service.EmailService;
import jakarta.validation.Valid;
import com.JanSahayak.AI.model.RefreshToken;
import com.JanSahayak.AI.service.RefreshTokenService;
import com.JanSahayak.AI.security.CurrentUser;
import com.JanSahayak.AI.dto.UserResponse;
import com.JanSahayak.AI.dto.request.VaultBlobRequest;
import com.JanSahayak.AI.security.IdentityBlindService;
import com.JanSahayak.AI.service.ActorProfileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
// Google OAuth2 id_token server-side verification (google-api-client)
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@RestController
@Slf4j
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${google.client.id}")
    private String googleClientId;

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepo userRepository;
    @Autowired private RoleRepo roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;
    @Autowired private UserService userService;
    @Autowired private com.JanSahayak.AI.service.PincodeValidationService pincodeValidationService;
    @Autowired private com.JanSahayak.AI.service.RateLimitingService rateLimitingService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Autowired private IdentityBlindService identityBlindService;
    @Autowired private ActorProfileService actorProfileService;
    @Autowired(required = false) private com.JanSahayak.AI.service.CivicPseudonymService civicPseudonymService;

    @GetMapping("/generate-pseudonym")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> generatePseudonym() {
        String pseudonym = (civicPseudonymService != null)
                ? civicPseudonymService.generateUniquePseudonym()
                : "Citizen" + (1000 + (int)(Math.random() * 9000));
        return ResponseEntity.ok(ApiResponse.success("Unique pseudonym generated", java.util.Map.of("username", pseudonym)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        if (rateLimitingService.isLoginBlocked(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many failed login attempts. Please try again after 15 minutes.", com.JanSahayak.AI.exception.ToastMessages.TOO_MANY_REQUESTS));
        }
        String emailHash = identityBlindService.deriveEmailHash(normalizedEmail);
        User maybeGoogleUser = (emailHash != null) 
                ? userRepository.findByEmailHashWithRole(emailHash).orElse(null) 
                : null;
        if (maybeGoogleUser == null && normalizedEmail != null) {
            maybeGoogleUser = userRepository.findByEmailWithRole(normalizedEmail).orElse(null);
        }
        if (maybeGoogleUser != null
                && "GOOGLE".equals(maybeGoogleUser.getAuthProvider())
                && maybeGoogleUser.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "This account uses Google sign-in. Please use 'Continue with Google' to log in.",
                            com.JanSahayak.AI.exception.ToastMessages.UNAUTHORIZED));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );

            // Check if email is verified (post-auth to prevent email enumeration)
            User user = (User) authentication.getPrincipal();
            if (user != null && user.isNormalUser() && !Boolean.TRUE.equals(user.getIsEmailVerified())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Please verify your email address. A verification link has been sent to your email.", com.JanSahayak.AI.exception.ToastMessages.UNAUTHORIZED));
            }

            // Clear failed attempts on successful login
            rateLimitingService.clearLoginAttempts(normalizedEmail);
            
            // Rotate session token to invalidate other active sessions
            String newSessionToken = userService.rotateSessionToken(user.getId());
            user.setSessionToken(newSessionToken);

            if (user.getActorSalt() == null) {
                user.setActorSalt(identityBlindService.generateActorSalt());
            }

            boolean hasVault = (user.getVaultBlob() != null && !user.getVaultBlob().isBlank());
            if (!hasVault && user.getSeedBlindSalt() == null) {
                user.setSeedBlindSalt(identityBlindService.generateActorSalt());
            }
            userRepository.save(user);

            String serverActorToken = identityBlindService.deriveServerActorToken(user);
            
            final String jwt = jwtUtil.generateToken(authentication, serverActorToken);

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
            ResponseCookie springCookie = ResponseCookie.from("refresh_token", refreshToken.getToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/api/auth/refresh")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("None")
                    .build();

            String effectiveUsername = user.getActualUsername();
            String effectiveProfileImage = user.getProfileImage();
            if (user.getRole() == null || "ROLE_USER".equals(user.getRole().getName())) {
                String token = identityBlindService.resolveActorTokenForUser(user);
                if (token != null && actorProfileService != null) {
                    com.JanSahayak.AI.model.ActorProfile ap = actorProfileService.findByActorToken(token).orElse(null);
                    if (ap != null) {
                        effectiveUsername = ap.getUsername();
                        if (ap.getProfileImage() != null) effectiveProfileImage = ap.getProfileImage();
                    }
                }
            }

            AuthResponse authResponse = AuthResponse.builder()
                    .token(jwt)
                    .serverActorToken(serverActorToken)
                    .vaultBlob(user.getVaultBlob())
                    .vaultSalt(user.getVaultSalt())
                    .seedBlindSalt(user.getSeedBlindSalt())
                    .hasVault(hasVault)
                    .role(user.getRole() != null ? user.getRole().getName() : "ROLE_USER")
                    .user(UserResponse.builder()
                            .id(user.getId())
                            .username(effectiveUsername)
                            .displayName(effectiveUsername)
                            .profileImage(effectiveProfileImage)
                            .bio(user.getBio())
                            .pincode(user.getPincode())
                            .isActive(user.getIsActive())
                            .createdAt(user.getCreatedAt())
                            .updatedAt(user.getUpdatedAt())
                            .role(user.getRole() != null ? user.getRole().getName() : "ROLE_USER")
                            .build())
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, springCookie.toString())
                    .body(ApiResponse.success("Login successful", authResponse));

        } catch (BadCredentialsException e) {
            rateLimitingService.recordFailedLogin(normalizedEmail);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Incorrect username or password. Please try again.", com.JanSahayak.AI.exception.ToastMessages.UNAUTHORIZED));
        } catch (UsernameNotFoundException e) {
            rateLimitingService.recordFailedLogin(normalizedEmail);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No account found with this email address.", com.JanSahayak.AI.exception.ToastMessages.UNAUTHORIZED));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Your account has been disabled. Please contact support.", com.JanSahayak.AI.exception.ToastMessages.UNAUTHORIZED));
        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", normalizedEmail, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An internal error occurred. Please try again later."));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshtoken(@CookieValue(name = "refresh_token", required = false) String requestRefreshToken) {
        if (requestRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Refresh Token is empty!"));
        }

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(rawUser -> {
                    User user = userRepository.findByIdWithRole(rawUser.getId()).orElse(rawUser);

                    // Rotate session token to invalidate other active sessions on refresh
                    String newSessionToken = userService.rotateSessionToken(user.getId());
                    user.setSessionToken(newSessionToken);
                    
                    String serverActorToken = (identityBlindService != null) ? identityBlindService.deriveServerActorToken(user) : null;
                    String token = jwtUtil.generateToken(user, serverActorToken);

                    boolean hasVault = (user.getVaultBlob() != null && !user.getVaultBlob().isBlank());
                    String roleName = user.getRole() != null ? user.getRole().getName() : "ROLE_USER";

                    AuthResponse authResponse = AuthResponse.builder()
                            .token(token)
                            .serverActorToken(serverActorToken)
                            .vaultBlob(user.getVaultBlob())
                            .vaultSalt(user.getVaultSalt())
                            .seedBlindSalt(user.getSeedBlindSalt())
                            .hasVault(hasVault)
                            .role(roleName)
                            .build();

                    return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
                })
                .orElseThrow(() -> new SecurityException("Refresh token is invalid or missing!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logoutUser(@CookieValue(name = "refresh_token", required = false) String requestRefreshToken) {
        if (requestRefreshToken != null) {
            refreshTokenService.findByToken(requestRefreshToken).ifPresent(token -> {
                refreshTokenService.deleteByUserId(token.getUser().getId());
            });
        }
        
        ResponseCookie springCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/auth/refresh")
                .maxAge(0)
                .sameSite("None")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, springCookie.toString())
                .body(ApiResponse.success("Log out successful", null));
    }

    @PostMapping("/register/citizen")
    public ResponseEntity<ApiResponse<String>> registerUser(@Valid @RequestBody RegisterRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        if (rateLimitingService.isRegistrationBlocked(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many registration requests. Please try again later.", com.JanSahayak.AI.exception.ToastMessages.TOO_MANY_REQUESTS));
        }
        try {
            rateLimitingService.recordRegistrationAttempt(normalizedEmail);
            // FIX: Use existsByEmail() instead of findByEmail().isPresent()
            // existsByEmail() runs a COUNT query — much cheaper than loading a full User entity
            // just to check if it exists.
            if (userService.existsByEmail(normalizedEmail)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists"));
            }

            Boolean hasInvalidPincode = false;
            try {
                if (!pincodeValidationService.isValidIndianPincode(request.getPincode())) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid Indian Pincode. Please enter a valid pincode."));
                }
            } catch (com.JanSahayak.AI.service.PincodeValidationService.ApiUnavailableException e) {
                log.warn("Pincode API unavailable during citizen registration. Falling back to regex validation.");
                // Regex validation already passed in service, we just mark it as unverified
                hasInvalidPincode = null;
            }

            User user = new User();
            user.setEmail(normalizedEmail);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            Role CitizenRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(CitizenRole);
            user.setPincode(request.getPincode());
            user.setHasInvalidPincode(hasInvalidPincode);
            
            // Age gating applied only to citizens
            user.setIsAdult(request.getIsAdult());
            
            // Set session token
            user.setSessionToken(UUID.randomUUID().toString());

            // Email Verification Setup
            String verificationToken = UUID.randomUUID().toString();
            user.setIsEmailVerified(false);
            user.setEmailVerificationToken(verificationToken);
            user.setEmailVerificationTokenExpiry(Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));

            // Generate opaque internal account key for citizens under Model 1
            int maxRetries = 10;
            boolean saved = false;

            for (int attempt = 0; attempt < maxRetries && !saved; attempt++) {
                try {
                    user.setUsername("acc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                    user.setActorSalt(identityBlindService.generateActorSalt());
                    user.setSeedBlindSalt(identityBlindService.generateActorSalt());
                    userService.saveUser(user);
                    saved = true;
                } catch (DataIntegrityViolationException e) {
                    log.debug("Account ID collision on attempt {}, retrying...", attempt + 1);
                }
            }

            if (!saved) {
                log.error("Failed to generate unique account key after {} attempts", maxRetries);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Unable to generate account key. Please try again."));
            }

            // Pre-mint initial anonymous ActorProfile for this citizen's initial actorToken
            if (identityBlindService != null) {
                String initialActorToken = identityBlindService.resolveActorTokenForUser(user);
                if (initialActorToken != null && actorProfileService != null) {
                    actorProfileService.createOrCopyFromUser(initialActorToken, user);
                }
            }

            // Send Verification Email
            emailService.sendVerificationEmail(user, verificationToken);

            return ResponseEntity.ok(ApiResponse.success("Registration successful! A verification link has been sent to your email."));

        } catch (Exception e) {
            log.error("Error during citizen registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed"));
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/register/department")
    public ResponseEntity<ApiResponse<String>> registerDepartment(@Valid @RequestBody RegisterRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        if (rateLimitingService.isRegistrationBlocked(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many registration requests. Please try again later.", com.JanSahayak.AI.exception.ToastMessages.TOO_MANY_REQUESTS));
        }
        try {
            rateLimitingService.recordRegistrationAttempt(normalizedEmail);
            // FIX: Use existsByEmail() instead of findByEmail().isPresent()
            if (userService.existsByEmail(normalizedEmail)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists"));
            }

            Boolean hasInvalidPincode = false;
            try {
                if (!pincodeValidationService.isValidIndianPincode(request.getPincode())) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid Indian Pincode. Please enter a valid pincode."));
                }
            } catch (com.JanSahayak.AI.service.PincodeValidationService.ApiUnavailableException e) {
                log.warn("Pincode API unavailable during department registration. Falling back to regex validation.");
                hasInvalidPincode = null;
            }

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username is required"));
            }

            if (userService.existsByUsername(request.getUsername().trim())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username already exists"));
            }

            User user = new User();
            user.setEmail(normalizedEmail);
            user.setUsername(request.getUsername().trim());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setIsEmailVerified(true);

            Role DepartmentRole = roleRepository.findByName("ROLE_DEPARTMENT")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(DepartmentRole);
            user.setPincode(request.getPincode());
            user.setHasInvalidPincode(hasInvalidPincode);
            user.setSessionToken(UUID.randomUUID().toString());

            userService.saveUser(user);
            log.info("Department user registered successfully: {}", user.getUsername());

            return ResponseEntity.ok(ApiResponse.success("Government department registered successfully"));

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during department registration: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Registration failed: Username or email already exists"));
        } catch (Exception e) {
            log.error("Error during department registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed due to server error"));
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping("/register/admin")
    public ResponseEntity<ApiResponse<String>> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        if (rateLimitingService.isRegistrationBlocked(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many registration requests. Please try again later.", com.JanSahayak.AI.exception.ToastMessages.TOO_MANY_REQUESTS));
        }
        try {
            rateLimitingService.recordRegistrationAttempt(normalizedEmail);
            if (userService.existsByEmail(normalizedEmail)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists"));
            }

            Boolean hasInvalidPincode = false;
            try {
                if (!pincodeValidationService.isValidIndianPincode(request.getPincode())) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid Indian Pincode. Please enter a valid pincode."));
                }
            } catch (com.JanSahayak.AI.service.PincodeValidationService.ApiUnavailableException e) {
                log.warn("Pincode API unavailable during admin registration. Falling back to regex validation.");
                hasInvalidPincode = null;
            }

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username is required"));
            }

            if (userService.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Username already exists"));
            }

            User user = new User();
            user.setEmail(normalizedEmail);
            user.setUsername(request.getUsername().trim());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setIsEmailVerified(true);

            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(adminRole);
            user.setPincode(request.getPincode());
            user.setHasInvalidPincode(hasInvalidPincode);
            user.setSessionToken(UUID.randomUUID().toString());

            userService.saveUser(user);
            log.info("Admin user registered successfully: {}", user.getUsername());

            return ResponseEntity.ok(ApiResponse.success("ADMIN has registered successfully"));

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during admin registration: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Registration failed: Username or email already exists"));
        } catch (Exception e) {
            log.error("Error during admin registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed due to server error"));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestParam("token") String token) {
        try {
            User user = userService.findByEmailVerificationToken(token).orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Invalid verification token."));
            }

            if (user.getEmailVerificationTokenExpiry().before(new Date())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Verification token has expired. Please request a new one."));
            }

            user.setIsEmailVerified(true);
            user.setEmailVerificationToken(null);
            user.setEmailVerificationTokenExpiry(null);
            userService.saveUser(user);

            return ResponseEntity.ok(ApiResponse.success("Email verified successfully! You can now log in."));

        } catch (Exception e) {
            log.error("Email verification failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An error occurred during verification."));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerification(@RequestParam("email") String email) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : null;
        if (rateLimitingService.isRegistrationBlocked(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many requests. Please try again later.", com.JanSahayak.AI.exception.ToastMessages.TOO_MANY_REQUESTS));
        }
        try {
            rateLimitingService.recordRegistrationAttempt(normalizedEmail);
            String emailHash = (identityBlindService != null) ? identityBlindService.deriveEmailHash(normalizedEmail) : null;
            User user = (emailHash != null ? userRepository.findByEmailHashWithRole(emailHash).orElse(null) : null);
            if (user == null) {
                user = userRepository.findByEmailWithRole(normalizedEmail).orElse(null);
            }
            if (user == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No account found with this email address."));
            }

            if (!"ROLE_USER".equals(user.getRole().getName())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Only citizen accounts require email verification."));
            }

            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This email is already verified."));
            }

            String token = UUID.randomUUID().toString();
            user.setEmailVerificationToken(token);
            user.setEmailVerificationTokenExpiry(Date.from(Instant.now().plus(24, ChronoUnit.HOURS)));
            userService.saveUser(user);

            emailService.sendVerificationEmail(user, token);

            return ResponseEntity.ok(ApiResponse.success("Verification email has been resent successfully."));

        } catch (Exception e) {
            log.error("Failed to resend verification email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resend verification email."));
        }
    }

    /**
     * POST /api/auth/google
     *
     * Two-phase Google OAuth2 endpoint:
     *
     * Phase 1 — { token } only:
     *   - Existing user → issue JWT + refresh token immediately.
     *   - New user    → return { message: "onboarding_required" } — NO account created yet.
     *
     * Phase 2 — { token, pincode, isAdult: true, acceptedPolicy: true }:
     *   - Validates Google token again (idempotent, secure).
     *   - Validates pincode via PincodeValidationService.
     *   - Creates account atomically with all fields populated.
     *   - Issues JWT + refresh token.
     *
     * Account is NEVER written to the DB until Phase 2 completes successfully.
     */
    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
        try {
            // 1. Verify Google id_token cryptographically using cached singleton verifier (<5ms)
            GoogleIdToken idToken = googleIdTokenVerifier.verify(request.getToken());
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or expired Google token. Please try again."));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email    = payload.getEmail().trim().toLowerCase();
            String googleId = payload.getSubject();
            // NOTE: We deliberately do NOT extract picture/name — privacy by design.

            // 2. Rate limit check
            if (rateLimitingService.isLoginBlocked(email)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("Too many requests. Please try again in 15 minutes."));
            }

            // 3. Look up existing user (hash-aware)
            String emailHash = identityBlindService.deriveEmailHash(email);
            User user = (emailHash != null)
                    ? userRepository.findByEmailHashWithRole(emailHash).orElse(null)
                    : null;
            if (user == null) {
                user = userRepository.findByEmailWithRole(email).orElse(null);
            }

            // ══ EXISTING USER — log in directly ══
            if (user != null) {
                // Link Google account if not already linked
                if (user.getGoogleId() == null) {
                    user.setGoogleId(googleId);
                    user.setAuthProvider(
                            "LOCAL".equals(user.getAuthProvider()) ? "LOCAL+GOOGLE" : "GOOGLE");
                }
                if (user.getActorSalt() == null) {
                    user.setActorSalt(identityBlindService.generateActorSalt());
                }
                rateLimitingService.clearLoginAttempts(email);
                String newSessionToken = java.util.UUID.randomUUID().toString();
                user.setSessionToken(newSessionToken);

                boolean hasVault = (user.getVaultBlob() != null && !user.getVaultBlob().isBlank());
                if (!hasVault && user.getSeedBlindSalt() == null) {
                    user.setSeedBlindSalt(identityBlindService.generateActorSalt());
                }
                userRepository.save(user);

                String serverActorToken = identityBlindService.deriveServerActorToken(user);

                final String jwt = jwtUtil.generateToken((UserDetails) user);
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

                String effectiveGoogleUsername = user.getActualUsername();
                String effectiveGoogleProfileImage = user.getProfileImage();
                if (user.getRole() == null || "ROLE_USER".equals(user.getRole().getName())) {
                    String token = identityBlindService.resolveActorTokenForUser(user);
                    if (token != null && actorProfileService != null) {
                        com.JanSahayak.AI.model.ActorProfile ap = actorProfileService.findByActorToken(token).orElse(null);
                        if (ap != null) {
                            effectiveGoogleUsername = ap.getUsername();
                            if (ap.getProfileImage() != null) effectiveGoogleProfileImage = ap.getProfileImage();
                        }
                    }
                }

                AuthResponse authResponse = AuthResponse.builder()
                        .token(jwt)
                        .serverActorToken(serverActorToken)
                        .vaultBlob(user.getVaultBlob())
                        .vaultSalt(user.getVaultSalt())
                        .seedBlindSalt(user.getSeedBlindSalt())
                        .hasVault(hasVault)
                        .role(user.getRole() != null ? user.getRole().getName() : "ROLE_USER")
                        .user(UserResponse.builder()
                                .id(user.getId())
                                .username(effectiveGoogleUsername)
                                .displayName(effectiveGoogleUsername)
                                .profileImage(effectiveGoogleProfileImage)
                                .bio(user.getBio())
                                .pincode(user.getPincode())
                                .isActive(user.getIsActive())
                                .createdAt(user.getCreatedAt())
                                .updatedAt(user.getUpdatedAt())
                                .role(user.getRole() != null ? user.getRole().getName() : "ROLE_USER")
                                .build())
                        .build();

                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken.getToken()).toString())
                        .body(ApiResponse.success("Login successful", authResponse));
            }

            // ══ NEW USER ══
            if (!request.isRegistrationRequest()) {
                // Phase 1: tell frontend to show onboarding form. NO DB write.
                return ResponseEntity.ok()
                        .body(ApiResponse.success("onboarding_required", null));
            }

            // Phase 2: validate and create account atomically
            if (!Boolean.TRUE.equals(request.getIsAdult())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("You must be 18 or older to create an account."));
            }
            if (!Boolean.TRUE.equals(request.getAcceptedPolicy())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("You must accept the Privacy Policy & Terms to continue."));
            }

            // Validate pincode
            String pincode = request.getPincode();
            Boolean hasInvalidPincode = false;
            try {
                if (!pincodeValidationService.isValidIndianPincode(pincode)) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Invalid Indian pincode. Please check and try again."));
                }
            } catch (com.JanSahayak.AI.service.PincodeValidationService.ApiUnavailableException e) {
                log.warn("Pincode validation API unavailable during Google registration; regex fallback used.");
                hasInvalidPincode = null; // marks for later re-validation
            }

            // Build the new user entity — fully populated
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setEmailHash(identityBlindService.deriveEmailHash(email));
            newUser.setActorSalt(identityBlindService.generateActorSalt());
            newUser.setSeedBlindSalt(identityBlindService.generateActorSalt());
            newUser.setPassword(null);           // Google users have no password
            newUser.setGoogleId(googleId);
            newUser.setAuthProvider("GOOGLE");
            newUser.setIsEmailVerified(true);    // Google already verified the email
            newUser.setIsAdult(true);
            newUser.setPincode(pincode);
            newUser.setHasInvalidPincode(hasInvalidPincode);
            newUser.setIsActive(true);
            newUser.setSessionToken(UUID.randomUUID().toString());
            // profileImage intentionally NOT set — user uploads their own later

            Role citizenRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("ROLE_USER not found in database"));
            newUser.setRole(citizenRole);

            // Persist with unique opaque account ID under Model 1
            boolean saved = false;
            for (int attempt = 0; attempt < 10 && !saved; attempt++) {
                try {
                    newUser.setUsername("acc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                    userService.saveUser(newUser);
                    saved = true;
                } catch (DataIntegrityViolationException e) {
                    log.debug("Account ID collision on Google registration attempt {}, retrying...", attempt + 1);
                }
            }
            if (!saved) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Could not generate account key. Please try again."));
            }

            // Pre-mint initial anonymous ActorProfile for this citizen
            String initialActorToken = identityBlindService.resolveActorTokenForUser(newUser);
            com.JanSahayak.AI.model.ActorProfile initialProfile = null;
            if (initialActorToken != null && actorProfileService != null) {
                initialProfile = actorProfileService.createOrCopyFromUser(initialActorToken, newUser);
            }

            rateLimitingService.clearLoginAttempts(email);
            final String jwt = jwtUtil.generateToken((UserDetails) newUser);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(newUser);

            String serverActorToken = identityBlindService.deriveServerActorToken(newUser);

            String responseUsername = initialProfile != null ? initialProfile.getUsername() : newUser.getActualUsername();
            String responseProfileImage = initialProfile != null && initialProfile.getProfileImage() != null
                    ? initialProfile.getProfileImage() : newUser.getProfileImage();

            AuthResponse authResponse = AuthResponse.builder()
                    .token(jwt)
                    .serverActorToken(serverActorToken)
                    .vaultBlob(newUser.getVaultBlob())
                    .vaultSalt(newUser.getVaultSalt())
                    .seedBlindSalt(newUser.getSeedBlindSalt())
                    .hasVault(false)
                    .role(newUser.getRole() != null ? newUser.getRole().getName() : "ROLE_USER")
                    .user(UserResponse.builder()
                            .id(newUser.getId())
                            .username(responseUsername)
                            .displayName(responseUsername)
                            .profileImage(responseProfileImage)
                            .bio(newUser.getBio())
                            .pincode(newUser.getPincode())
                            .isActive(newUser.getIsActive())
                            .createdAt(newUser.getCreatedAt())
                            .updatedAt(newUser.getUpdatedAt())
                            .role(newUser.getRole() != null ? newUser.getRole().getName() : "ROLE_USER")
                            .build())
                    .build();

            log.info("New Google user registered: id={} username={}", newUser.getId(), newUser.getUsername());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken.getToken()).toString())
                    .body(ApiResponse.success("Account created successfully", authResponse));

        } catch (Exception e) {
            log.error("Google authentication failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Authentication failed. Please try again."));
        }
    }

    /**
     * Builds the HttpOnly refresh_token cookie used in both regular login and Google login.
     * Kept DRY here to prevent the cookie config from drifting between endpoints.
     */
    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true)
                .path("/api/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("None")
                .build();
    }

    /**
     * FIX: Bounded username generation loop.
     *
     * The original do-while had no upper limit — if the username space were saturated,
     * it would loop forever burning DB connections. The outer retry guard in
     * registerUser() had a max of 10, but generateUniqueUsername() itself was unbounded.
     *
     * Fixed to try at most 20 combinations before throwing. Given ~150 adjectives ×
     * ~150 nouns × 9000 numbers = ~202,500,000 combinations, 20 attempts will succeed
     * in practice. The exception surfaces the same "try again" 500 that was already
     * shown to the user when the outer 10-retry loop failed.
     */
    private String generateUniqueUsername() {
        String[] adjectives = {
                "Happy","Brave","Swift","Clever","Mighty","Silent","Wise","Lucky","Bold","Shiny",
                "Fierce","Calm","Wild","Bright","Cool","Fast","Gentle","Sharp","Loyal","Kind",
                "Strong","Fearless","Quiet","Sneaky","Cheerful","Noble","Radiant","Epic","Smart","Eager",
                "Playful","Energetic","Glorious","Charming","Courageous","Heroic","Friendly","Polite","Magical","Mystic",
                "Joyful","Adventurous","Brilliant","Daring","Faithful","Generous","Humble","Creative","Graceful","Dynamic",
                "Witty","Keen","Determined","Sunny","Starry","Vivid","Dazzling","Zesty","Glowing","Chill",
                "Funky","Coolheaded","Quick","Resourceful","Inventive","Cheeky","Blissful","Hopeful","Valiant","Luminous",
                "Cosmic","Fiery","Dreamy","Tranquil","Golden","Boldhearted","Eternal","Zen","Spirited","Vast",
                "Skybound","Stellar","Brighthearted","Roaring","Free","Harmonic","Nimble","Gallant","Sturdy","Calmhearted",
                "Swiftfooted","Iron","Steady","Thunderous","Silentblade","Quickwitted","Stormy","Snowy","Frosty","Burning",
                "Shadowy","Crimson","Silver","Serene","Ancient","Wildhearted","Springtime","Moonlit","Sunlit","Windswept",
                "Electric","Neon","Galactic","Fabulous","Majestic","Ruthless","Jolly","Savage","Tough","Velvet",
                "Ambitious","Fearful","Furious","Curious","Pragmatic","Fanciful","Grandiose","Gleaming","Jumping","Sapphire",
                "Emerald","Ruby","Diamond","Platinum","Copper","Brass","Titanium","Quantum","Cyber","Lunar",
                "Solar","Astro","Meteor","Comet","Starlight","Nebula","Galaxy","Meteorite","Pulsar","Zealous",
                "Vibrant","Tenacious","Stoic","Resolute","Quaint","Proud","Optimistic","Mellow","Logical","Jubilant",
                "Invincible","Harmonious","Gritty","Enigmatic","Diligent","Auspicious","Astute","Audacious","Brawny","Candid",
                "Dapper","Earnest","Flawless","Gleeful","Hardy","Intrepid","Jovial","Kooky","Lithe","Merry",
                "Nifty","Outrageous","Peppy","Quirky","Rambunctious","Sassy","Snazzy","Spiffy","Swanky","Upbeat",
                "Vivacious","Whimsical","Zippy"
        };

        String[] nouns = {
                "Tiger","Eagle","Shark","Panther","Wolf","Falcon","Lion","Bear","Hawk","Cheetah",
                "Puma","Dragon","Phoenix","Leopard","Viper","Cobra","Fox","Jaguar","Lynx","Raven",
                "Crow","Owl","Stallion","Mustang","Horse","Buffalo","Bison","Bull","Ram","Goat",
                "Deer","Moose","Elk","Yak","Elephant","Rhino","Hippo","Gorilla","Chimp","Orangutan",
                "Whale","Dolphin","Seal","Otter","Penguin","PolarBear","Camel","Giraffe","Kangaroo","Koala",
                "Crocodile","Alligator","Turtle","Tortoise","Frog","Toad","Eel","Octopus","Squid","Jellyfish",
                "Starfish","Crab","Lobster","Shrimp","Mantis","Scorpion","Spider","Beetle","Wasp","Hornet",
                "Ant","Bee","Butterfly","Moth","Dragonfly","Ladybug","Firefly","Bat","Rat","Mouse",
                "Squirrel","Chipmunk","Porcupine","Hedgehog","Ferret","Badger","Weasel","Armadillo","Sloth","Anteater",
                "Parrot","Macaw","Canary","Sparrow","Robin","Finch","Swallow","Seagull","Pelican","Albatross",
                "Heron","Flamingo","Swan","Duck","Goose","Turkey","Chicken","Rooster","Peacock","Dove",
                "Pigeon","Caterpillar","Worm","Snail","Slug","Clam","Oyster","Mussel","Coral","Barnacle",
                "Griffin","Hydra","Cerberus","Unicorn","Pegasus","Minotaur","Yeti","Bigfoot","Werewolf","Ghoul",
                "Sphinx","Chimera","Basilisk","Hippogriff","Mermaid","Wyvern","Leviathan","Titan","Golem","Ninja",
                "Samurai","Wizard","Knight","Ranger","Pirate","Cyborg","Robot","Alien","Astronaut","Warrior",
                "Paladin","Mage","Sorcerer","Warlock","Druid","Bard","Cleric","Monk","Rogue","Vampire",
                "Zombie","Ghost","Phantom","Specter","Banshee","Goblin","Orc","Troll","Ogre","Kraken",
                "Cyclops","Gargoyle","Wraith","Lich","Valkyrie","Imp","Demon","Alchemist","Archer","Assassin",
                "Barbarian","Beast","Berserker","Centaur","Champion","Conjurer","Crusader","Deity","Diviner","Elemental",
                "Elf","Enchanter","Explorer","Fighter","Gladiator","Guardian","Healer","Hunter","Illusionist","Invoker",
                "Jester","Juggernaut","King","Legend","Lord","Mercenary","Mutant","Necromancer","Oracle","Outlaw",
                "Pioneer","Prince","Prophet","Queen","Rebel","Sage","Savant","Scholar","Scout","Seer",
                "Sentinel","Shaman","Sniper","Soldier","Summoner","Templar","Thief","Warlord","Weaver","Zephyr",
                "Aardvark","Alpaca","Antelope","Baboon","Bandicoot","Bobcat","Capybara","Caribou","Cassowary","Chinchilla",
                "Cougar","Coyote","Dingo","Echidna","Emu","Gazelle","Gibbon","Gopher","GuineaPig","Hamster",
                "Hyena","Iguana","Impala","Jackal","Jackrabbit","Lemur","Llama","Macaque","Mandrill","Marmoset",
                "Marmot","Meerkat","Mongoose","Ocelot"
        };

        for (int i = 0; i < 20; i++) {
            String adjective = adjectives[(int) (Math.random() * adjectives.length)];
            String noun      = nouns[(int) (Math.random() * nouns.length)];
            int    number    = (int) (Math.random() * 9000) + 1000;
            String candidate = adjective + noun + number;

            if (candidate.length() > 3 && !userService.existsByUsername(candidate)) {
                return candidate;
            }
        }

        // Practically unreachable given 200M+ combinations, but we must be bounded.
        throw new ServiceException("Could not generate a unique username after 20 attempts. Please try again.");
    }

    @GetMapping("/verify-email-update")
    public ResponseEntity<ApiResponse<Void>> verifyEmailUpdate(@RequestParam String token) {
        try {
            userService.verifyEmailUpdate(token);
            return ResponseEntity.ok(ApiResponse.success("Email successfully updated", null));
        } catch (ValidationException e) {
            log.warn("Email update verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Verification failed", e.getMessage()));
        } catch (ServiceException e) {
            log.error("Service error in verifyEmailUpdate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Service error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in verifyEmailUpdate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("An unexpected error occurred during email verification"));
        }
    }

    @PostMapping({"/vault-blob", "/vault/setup"})
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> saveVaultBlob(
            @RequestBody @Valid VaultBlobRequest request,
            @CurrentUser User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        if (request.getVaultBlob() == null || request.getVaultBlob().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vault blob cannot be empty"));
        }
        currentUser.setVaultBlob(request.getVaultBlob());
        currentUser.setVaultSalt(request.getVaultSalt());
        currentUser.setSeedBlindSalt(null); // Purge seedBlindSalt once client encrypts their vault
        userRepository.save(currentUser);

        // If actorToken is provided, register/update actor_profile copying ALL persona fields from User
        if (request.getActorToken() != null && !request.getActorToken().isBlank()) {
            actorProfileService.createOrCopyFromUser(request.getActorToken(), currentUser, request.getUsername());
        }

        return ResponseEntity.ok(ApiResponse.success("Vault blob saved successfully and seed salt purged", "OK"));
    }

    @PostMapping("/reset-vault")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<String>> resetVault(
            @RequestBody @Valid VaultBlobRequest request,
            @CurrentUser User currentUser) {
        return saveVaultBlob(request, currentUser);
    }

    @PostMapping("/vault/init-reset")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_DEPARTMENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> initVaultReset(@CurrentUser User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        currentUser.setVaultBlob(null);
        currentUser.setVaultSalt(null);
        String freshSeedSalt = identityBlindService.generateActorSalt();
        currentUser.setSeedBlindSalt(freshSeedSalt);
        userRepository.save(currentUser);

        return ResponseEntity.ok(ApiResponse.success(
                "Vault reset initiated. Use the provided seedBlindSalt to derive a new actor token and encrypt new vault.",
                java.util.Map.of("seedBlindSalt", freshSeedSalt)
        ));
    }
}
