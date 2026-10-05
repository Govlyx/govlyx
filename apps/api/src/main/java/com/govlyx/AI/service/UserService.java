package com.govlyx.AI.service;

import com.govlyx.AI.dto.PaginatedResponse;
import com.govlyx.AI.dto.UserTagSuggestionDto;
import com.govlyx.AI.config.Constant;
import com.govlyx.AI.enums.PostStatus;
import com.govlyx.AI.exception.*;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.PostRepo;
import com.govlyx.AI.repository.PincodeLookupRepo;
import com.govlyx.AI.repository.UserRepo;
import com.govlyx.AI.repository.UserTagRepo;
import com.govlyx.AI.repository.UserPassRepository;
import com.govlyx.AI.payload.PaginationUtils;
import com.govlyx.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.govlyx.AI.exception.ValidationException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepo userRepository;
    private final PostRepo postRepository;
    private final UserTagRepo userTagRepository;
    private final PinCodeLookupService pincodeLookupService;
    private final PincodeLookupRepo pincodeLookupRepository;
    private final PasswordEncoder passwordEncoder;
    private final PostInteractionService postInteractionService;
    private final RateLimitingService rateLimitingService;
    private final UserPassRepository userPassRepository;
    private final org.springframework.cache.CacheManager cacheManager;
    private final EmailService emailService;
    private final com.govlyx.AI.security.IdentityBlindService identityBlindService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.govlyx.AI.repository.ActorProfileRepo actorProfileRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.govlyx.AI.service.ActorProfileService actorProfileService;

    private UserService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy UserService self) {
        this.self = self;
    }

    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            String emailHash = (email != null && identityBlindService != null) ? identityBlindService.deriveEmailHash(email) : null;
            User user = (emailHash != null ? userRepository.findByEmailHash(emailHash) : Optional.<User>empty())
                    .or(() -> userRepository.findByEmail(email))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            if (!user.getIsActive()) {
                throw new UsernameNotFoundException("User account is inactive: " + email);
            }

            return buildUserDetails(user);
        } catch (Exception e) {
            log.error("Failed to load user: {}", email, e);
            throw new UsernameNotFoundException("Failed to load user: " + email);
        }
    }

    @Cacheable(value = Constant.CACHE_USER_PROFILE, key = "#authentication.name", unless = "#result == null")
    public User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ValidationException("User not authenticated");
        }

        // Fast-path: If principal is already loaded User with id, load eagerly with role
        if (authentication.getPrincipal() instanceof User) {
            User principal = (User) authentication.getPrincipal();
            if (principal.getId() != null) {
                User user = userRepository.findByIdWithRole(principal.getId()).orElse(principal);
                if (user.getIsActive() == null || !user.getIsActive()) {
                    throw new ValidationException("User account is inactive");
                }
                return user;
            }
        }

        String identifier = authentication.getName();
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("Invalid authentication - no identifier found");
        }

        String emailHash = (identifier != null && identityBlindService != null) ? identityBlindService.deriveEmailHash(identifier) : null;

        User user = (emailHash != null ? userRepository.findByEmailHashWithRole(emailHash) : Optional.<User>empty())
                .or(() -> userRepository.findByEmailHashWithRole(identifier))
                .or(() -> userRepository.findByUsernameWithRole(identifier))
                .orElseThrow(() -> new UserNotFoundException("User not found with identifier: " + identifier));

        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new ValidationException("User account is inactive");
        }

        return user;
    }

    // ===== User Lookup Methods =====

    public User findByUsername(String username) {
        try {
            // Use JOIN FETCH to eagerly load the Role in one query.
            // Role is FetchType.LAZY — without this, accessing user.getRole().getName()
            // outside a Hibernate session throws LazyInitializationException.
            User user = userRepository.findByUsernameWithRole(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

            return user;
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find user by username: {}", username, e);
            throw new UserNotFoundException("Failed to find user: " + e.getMessage(), e);
        }
    }

    public User findById(Long userId) {
        try {
            PostUtility.validateUserId(userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

            return user;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find user by ID: {}", userId, e);
            throw new UserNotFoundException("Failed to find user: " + e.getMessage(), e);
        }
    }

    // ===== Department User Methods Using Pincode Prefix Logic =====

    public PaginatedResponse<User> findDepartmentUsersByPincode(String pincode, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupGeographicPagination(
                    "findDepartmentUsersByPincode", beforeId, limit, "pincode");

            if (!Constant.isValidIndianPincode(pincode)) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByRoleNameAndPincodeAndIsActiveTrueAndIdLessThanOrderByIdDesc(
                        Constant.ROLE_DEPARTMENT, pincode, setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByRoleNameAndPincodeAndIsActiveTrueOrderByIdDesc(
                        Constant.ROLE_DEPARTMENT, pincode, pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("findDepartmentUsersByPincode",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to find department users by pincode: {}", pincode, e);
            return PaginationUtils.handlePaginationError("findDepartmentUsersByPincode", e,
                    PaginationUtils.validateGeographicSearchLimit(limit, "pincode"));
        }
    }

    public PaginatedResponse<User> findDepartmentUsersByState(String state, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupGeographicPagination(
                    "findDepartmentUsersByState", beforeId, limit, "state");

            if (state == null || state.trim().isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(List.of(state.trim()),
                    pincodeLookupService);
            if (statePrefixes.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<User> departmentUsers = new ArrayList<>();
            for (String prefix : statePrefixes) {
                Pageable pageable = PaginationUtils.createPageable(setup);
                List<User> users;
                if (setup.hasCursor()) {
                    users = userRepository
                            .findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
                                    Constant.ROLE_DEPARTMENT, prefix, setup.getSanitizedCursor(), pageable);
                } else {
                    users = userRepository.findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
                            Constant.ROLE_DEPARTMENT, prefix, pageable);
                }
                if (users != null) {
                    departmentUsers.addAll(users);
                }
            }

            List<User> distinctUsers = departmentUsers.stream()
                    .distinct()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(distinctUsers,
                    setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("findDepartmentUsersByState",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to find department users by state: {}", state, e);
            return PaginationUtils.handlePaginationError("findDepartmentUsersByState", e,
                    PaginationUtils.validateGeographicSearchLimit(limit, "state"));
        }
    }

    public PaginatedResponse<User> findDepartmentUsersByDistrict(String state, String district, Long beforeId,
            Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupGeographicPagination(
                    "findDepartmentUsersByDistrict", beforeId, limit, "district");

            if (state == null || district == null || state.trim().isEmpty() || district.trim().isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<String> districtPrefixes = PostUtility.convertDistrictsToPincodePrefixes(
                    List.of(district.trim()), pincodeLookupService);
            if (districtPrefixes.isEmpty()) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            List<User> departmentUsers = new ArrayList<>();
            for (String prefix : districtPrefixes) {
                Pageable pageable = PaginationUtils.createPageable(setup);
                List<User> users;
                if (setup.hasCursor()) {
                    users = userRepository
                            .findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
                                    Constant.ROLE_DEPARTMENT, prefix, setup.getSanitizedCursor(), pageable);
                } else {
                    users = userRepository.findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
                            Constant.ROLE_DEPARTMENT, prefix, pageable);
                }
                if (users != null) {
                    departmentUsers.addAll(users);
                }
            }

            List<String> statePrefixes = PostUtility.convertStatesToPincodePrefixes(List.of(state.trim()),
                    pincodeLookupService);
            if (!statePrefixes.isEmpty()) {
                String statePrefix = statePrefixes.get(0);
                departmentUsers = departmentUsers.stream()
                        .filter(user -> user.hasPincode() && user.getPincode().startsWith(statePrefix))
                        .collect(Collectors.toList());
            }

            List<User> distinctUsers = departmentUsers.stream()
                    .distinct()
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(distinctUsers,
                    setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("findDepartmentUsersByDistrict",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to find department users by district: {} - {}", state, district, e);
            return PaginationUtils.handlePaginationError("findDepartmentUsersByDistrict", e,
                    PaginationUtils.validateGeographicSearchLimit(limit, "district"));
        }
    }

    // ===== User Search and Tagging Methods =====

    public PaginatedResponse<UserTagSuggestionDto> searchUsersForTagging(String query, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupTaggingPagination(
                    "searchUsersForTagging", beforeId, limit);

            if (query == null || query.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            String cleanQuery = query.startsWith("@") ? query.substring(1) : query;
            if (cleanQuery.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.searchUsersForTaggingWithCursor(cleanQuery.trim(),
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.searchUsersForTagging(cleanQuery.trim(), pageable);
            }

            List<UserTagSuggestionDto> userDtos = users.stream()
                    .map(this::convertToUserTagSuggestion)
                    .collect(Collectors.toList());

            PaginatedResponse<UserTagSuggestionDto> response = PaginationUtils.createIdBasedResponse(
                    userDtos, setup.getValidatedLimit(), UserTagSuggestionDto::getId);

            PaginationUtils.logPaginationResults("searchUsersForTagging",
                    userDtos, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to search users for tagging with query: {}", query, e);
            return PaginationUtils.handlePaginationError("searchUsersForTagging", e,
                    PaginationUtils.validateTaggingLimit(limit));
        }
    }

    // ===== Geographic Distribution Methods Using Pincode Prefix Logic =====

    @Cacheable(value = "user-distribution-pincode")
    public Map<String, Long> getUserDistributionByPincode() {
        try {
            List<Object[]> stats = userRepository.getUserDistributionByPincode();

            Map<String, Long> result = new LinkedHashMap<>();
            for (Object[] stat : stats) {
                String pincode = (String) stat[0];
                Long count = (Long) stat[1];

                if (Constant.isValidIndianPincode(pincode)) {
                    result.put(pincode, count);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get user distribution by pincode", e);
            throw new ServiceException("Failed to get user distribution by pincode: " + e.getMessage(), e);
        }
    }

    public Map<String, Long> getUserDistributionByState() {
        try {
            // FIX MEMORY PRESSURE: previously called findByPincodeIsNotNullAndIsActive
            // TrueAndPincodeMatches() which loaded ALL active users with pincodes into
            // heap for an in-memory groupBy. On large user bases this is O(ALL_USERS)
            // heap allocation.
            // Fix: use getUserDistributionByPincode() which is already a DB-aggregate
            // query (GROUP BY pincode, COUNT). We then roll those up to state level
            // in memory — the pincode-count map is far smaller than the user list.
            Map<String, Long> byPincode = self.getUserDistributionByPincode(); // DB aggregate

            Map<String, Long> result = new LinkedHashMap<>();
            Map<String, Long> prefixCounts = new LinkedHashMap<>();

            for (Map.Entry<String, Long> entry : byPincode.entrySet()) {
                String pincode = entry.getKey();
                if (!Constant.isValidIndianPincode(pincode))
                    continue;
                String prefix = pincode.substring(0, 2); // state prefix = first 2 digits
                prefixCounts.merge(prefix, entry.getValue(), Long::sum);
            }

            for (Map.Entry<String, Long> entry : prefixCounts.entrySet()) {
                String statePrefix = entry.getKey();
                if (!Constant.isValidIndianStatePrefix(statePrefix))
                    continue;
                List<com.govlyx.AI.model.PincodeLookup> samplePincodes = pincodeLookupService
                        .findByStatePrefix(statePrefix);
                String stateName = samplePincodes.isEmpty()
                        ? "State-" + statePrefix
                        : samplePincodes.get(0).getState();
                result.put(stateName, entry.getValue());
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get user distribution by state", e);
            throw new ServiceException("Failed to get user distribution by state: " + e.getMessage(), e);
        }
    }

    // ===== Permission Methods Using Pincode Prefix Logic =====

    public boolean canUserResolvePostsInPincode(User user, String pincode) {
        return PostUtility.canUserResolvePostsInPincode(user, pincode);
    }

    // ===== User Update Methods =====

    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user) {
        try {
            User existingUser = findById(user.getId());
            String oldEmail = existingUser.getEmail();

            if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
                if (existsByEmail(user.getEmail())) {
                    throw new ValidationException("Email already exists: " + user.getEmail());
                }
                // Do not update the primary email immediately.
                // Set pending email and generate a verification token.
                String token = java.util.UUID.randomUUID().toString();
                existingUser.setPendingEmail(user.getEmail());
                existingUser.setEmailUpdateToken(token);
                existingUser.setEmailUpdateTokenExpiry(new java.util.Date(System.currentTimeMillis() + 86400000L)); // 24 hours
                
                // Send verification emails
                emailService.sendEmailUpdateAlertEmail(existingUser);
                emailService.sendEmailUpdateVerificationEmail(existingUser, token, user.getEmail());
            }
            if (user.getBio() != null) {
                existingUser.setBio(org.springframework.web.util.HtmlUtils.htmlEscape(user.getBio()));
            }
            boolean pincodeChanged = false;
            if (user.getPincode() != null && !user.getPincode().equals(existingUser.getPincode())) {
                PostUtility.validateTargetPincodeForUser(user.getPincode());

                if (!pincodeLookupService.isValidPincode(user.getPincode())) {
                    throw new ValidationException("Indian pincode not found in system: " + user.getPincode());
                }
                existingUser.setPincode(user.getPincode());
                pincodeChanged = true;

                // Forced GPS Synchronisation (Senior Dev Review — Point 2: Data Integrity Drift)
                // When a user manually changes their pincode, we MUST overwrite their saved GPS
                // coordinates to match the new pincode's center. This prevents the situation where
                // the NEARBY feed shows Mumbai results for a user who has moved to Delhi and updated
                // only their pincode. The live-device GPS (if provided simultaneously) takes
                // precedence and will overwrite this pincode-center coordinate.
                pincodeLookupRepository.findById(user.getPincode()).ifPresent(pincodeData -> {
                    if (pincodeData.getLatitude() != null && pincodeData.getLongitude() != null) {
                        existingUser.setHomeLatitude(pincodeData.getLatitude());
                        existingUser.setHomeLongitude(pincodeData.getLongitude());
                        log.info("[GPS-Sync] Auto-synced homeLatitude/homeLongitude for user {} from pincode {}",
                                existingUser.getId(), user.getPincode());
                    }
                });
            }

            // Map new localization and moderation fields
            if (user.getInterfaceLanguage() != null) {
                existingUser.setInterfaceLanguage(user.getInterfaceLanguage());
            }
            if (user.getPreferredLanguage() != null) {
                existingUser.setPreferredLanguage(user.getPreferredLanguage());
            }
            if (user.getAutoTranslate() != null) {
                existingUser.setAutoTranslate(user.getAutoTranslate());
            }
            if (user.getProfanityFilterLevel() != null) {
                existingUser.setProfanityFilterLevel(user.getProfanityFilterLevel());
            }
            if (user.getMutedWords() != null) {
                existingUser.setMutedWords(user.getMutedWords());
            }
            if (user.getBlockedActors() != null) {
                existingUser.setBlockedActors(user.getBlockedActors());
            }

            User updatedUser = userRepository.save(existingUser);

            // Keep ActorProfile persona in sync with User entity
            syncActorProfile(updatedUser);

            // Programmatic cache eviction to prevent orphaned cache keys
            if (cacheManager != null) {
                org.springframework.cache.Cache authCache = cacheManager.getCache("authUserDetails");
                if (authCache != null) authCache.evict(updatedUser.getId());

                org.springframework.cache.Cache profileCache = cacheManager.getCache(Constant.CACHE_USER_PROFILE);
                if (profileCache != null) {
                    if (oldEmail != null) profileCache.evict(oldEmail);
                    if (updatedUser.getEmail() != null) profileCache.evict(updatedUser.getEmail());
                    if (updatedUser.getEmailHash() != null) profileCache.evict(updatedUser.getEmailHash());
                }

                if (pincodeChanged) {
                    org.springframework.cache.Cache feedCache = cacheManager.getCache("hlig_feed");
                    if (feedCache != null) {
                        feedCache.evict(updatedUser.getId() + "_HOT");
                        feedCache.evict(updatedUser.getId() + "_NEW");
                        feedCache.evict(updatedUser.getId() + "_TOP");
                    }
                }
            }

            log.info("Updated user: {} with role: {}, pincode: {}",
                    updatedUser.getUsername(), updatedUser.getRole().getName(),
                    updatedUser.getPincode() != null ? updatedUser.getPincode() : "none");

            return updatedUser;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update user: {}", user.getId(), e);
            throw new ServiceException("Failed to update user: " + e.getMessage(), e);
        }
    }

    public void syncActorProfile(User user) {
        if (user == null || actorProfileRepo == null) return;
        try {
            String actorToken = (identityBlindService != null) ? identityBlindService.resolveActorTokenForUser(user) : null;
            java.util.Optional<com.govlyx.AI.model.ActorProfile> profileOpt = java.util.Optional.empty();
            if (actorToken != null && !actorToken.isBlank()) {
                profileOpt = actorProfileRepo.findByActorToken(actorToken);
            }
            if (profileOpt.isEmpty()) {
                profileOpt = actorProfileRepo.findByUsername(user.getActualUsername());
            }
            profileOpt.ifPresent(profile -> {
                if (actorProfileService != null) {
                    actorProfileService.copyUserPreferencesToProfile(user, profile);
                } else {
                    if (user.getProfileImage() != null) profile.setProfileImage(user.getProfileImage());
                    if (user.getBio() != null) profile.setBio(user.getBio());
                    if (user.getPincode() != null) profile.setPincode(user.getPincode());
                    if (user.getHomeLatitude() != null) profile.setHomeLatitude(user.getHomeLatitude());
                    if (user.getHomeLongitude() != null) profile.setHomeLongitude(user.getHomeLongitude());
                    profile.setUpdatedAt(new java.util.Date());
                }
                actorProfileRepo.save(profile);
            });
        } catch (Exception e) {
            log.warn("Failed to sync ActorProfile for user {}: {}", user.getId(), e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePincode(User user, String pincode) {
        User existingUser = (user.getId() != null) ? findById(user.getId()) : user;
        existingUser.setPincode(pincode);
        existingUser.setHasInvalidPincode(false);

        // Forced GPS center sync
        pincodeLookupRepository.findById(pincode).ifPresent(pincodeData -> {
            if (pincodeData.getLatitude() != null && pincodeData.getLongitude() != null) {
                existingUser.setHomeLatitude(pincodeData.getLatitude());
                existingUser.setHomeLongitude(pincodeData.getLongitude());
                log.info("[GPS-Sync] Auto-synced homeLatitude/homeLongitude for user {} from pincode {}",
                        existingUser.getId(), pincode);
            }
        });

        User savedUser = userRepository.save(existingUser);
        syncActorProfile(savedUser);

        if (cacheManager != null) {
            org.springframework.cache.Cache authCache = cacheManager.getCache("authUserDetails");
            if (authCache != null) authCache.evict(savedUser.getId());
            
            org.springframework.cache.Cache feedCache = cacheManager.getCache("hlig_feed");
            if (feedCache != null) {
                feedCache.evict(savedUser.getId() + "_HOT");
                feedCache.evict(savedUser.getId() + "_NEW");
                feedCache.evict(savedUser.getId() + "_TOP");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public User saveUser(User user) {
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank() && user.getEmailHash() == null && identityBlindService != null) {
            user.setEmailHash(identityBlindService.deriveEmailHash(user.getEmail()));
        }
        return userRepository.save(user);
    }

    public Optional<User> findByEmailVerificationToken(String token) {
        return userRepository.findByEmailVerificationToken(token);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        String emailHash = identityBlindService != null ? identityBlindService.deriveEmailHash(email) : null;
        if (emailHash != null) {
            Optional<User> user = userRepository.findByEmailHash(emailHash);
            if (user.isPresent()) return user;
        }
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) return false;
        String emailHash = identityBlindService != null ? identityBlindService.deriveEmailHash(email) : null;
        if (emailHash != null && userRepository.existsByEmailHash(emailHash)) {
            return true;
        }
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = Constant.CACHE_USER_PROFILE, key = "#user.username")
    public void updateProfileImage(User user, String imageUrl) {
        user.setProfileImage(imageUrl);
        userRepository.save(user);
        syncActorProfile(user);
    }

    // ===== New Paginated User Listing Methods =====

    public PaginatedResponse<User> getAllActiveUsers(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getAllActiveUsers", beforeId, limit,
                    Constant.DEFAULT_ACTIVE_USER_LIMIT, Constant.MAX_ACTIVE_USER_LIMIT);

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByIsActiveTrueAndIdLessThanOrderByIdDesc(
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByIsActiveTrueOrderByIdDesc(pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getAllActiveUsers",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get active users with pagination", e);
            return PaginationUtils.handlePaginationError("getAllActiveUsers", e,
                    PaginationUtils.validateLimit(limit, Constant.DEFAULT_ACTIVE_USER_LIMIT,
                            Constant.MAX_ACTIVE_USER_LIMIT));
        }
    }

    public PaginatedResponse<User> getUsersByRole(String roleName, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getUsersByRole", beforeId, limit,
                    Constant.DEFAULT_USER_SEARCH_LIMIT, Constant.MAX_USER_SEARCH_LIMIT);

            Pageable pageable = PaginationUtils.createPageable(setup);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByRoleNameAndIdLessThanOrderByIdDesc(roleName,
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByRoleNameOrderByIdDesc(roleName, pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getUsersByRole",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get users by role: {}", roleName, e);
            return PaginationUtils.handlePaginationError("getUsersByRole", e,
                    PaginationUtils.validateUserSearchLimit(limit));
        }
    }

    public PaginatedResponse<User> getRecentlyCreatedUsers(Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "getRecentlyCreatedUsers", beforeId, limit,
                    Constant.DEFAULT_USER_SEARCH_LIMIT, Constant.MAX_USER_SEARCH_LIMIT);

            Pageable pageable = PaginationUtils.createPageable(setup);

            Timestamp recentCreationThreshold = new Timestamp(
                    System.currentTimeMillis() - Constant.RECENT_ACTIVITY_MILLIS);

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
                        recentCreationThreshold, setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(
                        recentCreationThreshold, pageable);
            }

            PaginatedResponse<User> response = PaginationUtils.createUserResponse(
                    users != null ? users : List.of(), setup.getValidatedLimit());

            PaginationUtils.logPaginationResults("getRecentlyCreatedUsers",
                    response.getData(), response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to get recently created users", e);
            return PaginationUtils.handlePaginationError("getRecentlyCreatedUsers", e,
                    PaginationUtils.validateUserSearchLimit(limit));
        }
    }

    // ===== Theme Update =====

    @Transactional(rollbackFor = Exception.class)
    public void updateTheme(Long userId, String theme) {
        try {
            User user = findById(userId);
            user.setTheme(theme);
            userRepository.save(user);

            if (actorProfileRepo != null) {
                actorProfileRepo.findByUsername(user.getActualUsername()).ifPresent(profile -> {
                    profile.setTheme(theme);
                    profile.setUpdatedAt(new java.util.Date());
                    actorProfileRepo.save(profile);
                });
            }

            // Evict user caches so that subsequent /api/users/me queries immediately reflect the new theme
            if (cacheManager != null) {
                org.springframework.cache.Cache authCache = cacheManager.getCache("authUserDetails");
                if (authCache != null) {
                    authCache.evict(userId);
                }

                org.springframework.cache.Cache profileCache = cacheManager.getCache(Constant.CACHE_USER_PROFILE);
                if (profileCache != null) {
                    if (user.getEmail() != null) profileCache.evict(user.getEmail());
                    if (user.getEmailHash() != null) profileCache.evict(user.getEmailHash());
                }
            }
        } catch (Exception e) {
            log.error("Failed to update theme for user ID: {}", userId, e);
            throw new ServiceException("Failed to update theme", e);
        }
    }

    // ===== Password Update =====

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "authUserDetails", key = "#userId")
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        try {
            User user = findById(userId);

            // Guard: Google-only users have no password (null). BCryptPasswordEncoder.matches()
            // throws IllegalArgumentException if the stored password is null — guard against that.
            if (user.getPassword() == null || "GOOGLE".equals(user.getAuthProvider())) {
                throw new ValidationException(
                        "Google sign-in accounts do not have a password. " +
                        "You can set a password in Settings to enable email/password login.");
            }

            String rateLimitKey = user.getEmailHash() != null ? user.getEmailHash() : (user.getEmail() != null ? user.getEmail() : String.valueOf(user.getId()));

            if (rateLimitingService.isPasswordChangeBlocked(rateLimitKey)) {
                throw new ValidationException("Too many failed password change attempts. Please try again after 15 minutes.");
            }
            
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                rateLimitingService.recordFailedPasswordChange(rateLimitKey);
                throw new ValidationException("Incorrect current password.");
            }
            if (newPassword == null || !newPassword.matches("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!.*_\\-])(?=\\S+$).{8,20}$")) {
                throw new ValidationException("Password must be 8-20 characters long and contain at least one digit, one lowercase, one uppercase, one special character, and no whitespace.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            rateLimitingService.clearPasswordChangeAttempts(rateLimitKey);
            log.info("Password updated successfully for user ID: {}", userId);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to change password for user ID: {}", userId, e);
            throw new ServiceException("Failed to change password: " + e.getMessage(), e);
        }
    }

    // ===== Account Deletion =====

    /**
     * Soft-deactivate a user account and clean up all their interaction data
     * (saved posts, shares). Hard deletion of posts/comments is handled by a
     * separate admin flow or scheduled job.
     *
     * @param userId      ID of the account to deactivate
     * @param currentUser authenticated user performing the action (must be self or
     *                    admin)
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "authUserDetails", key = "#userId")
    public void deactivateUser(Long userId, User currentUser) {
        try {
            PostUtility.validateUserId(userId);
            PostUtility.validateUser(currentUser);

            boolean isSelf = currentUser.getId().equals(userId);
            boolean isAdmin = PostUtility.isAdmin(currentUser);
            if (!isSelf && !isAdmin) {
                throw new SecurityException("Only the account owner or an admin can deactivate this account.");
            }

            User user = findById(userId);
            if (user.getIsActive() == null || !user.getIsActive()) {
                throw new ValidationException("Account is already inactive.");
            }

            // Clean up all saves and shares before deactivating
            try {
                postInteractionService.cleanupForUserDeletion(user);
            } catch (Exception e) {
                log.warn("Failed to clean up interactions for user={}: {}", userId, e.getMessage());
            }

            user.setIsActive(false);

            // Allow re-registration by freeing up the email and username
            String timeSuffix = "_del_" + System.currentTimeMillis();
            
            String originalEmail = user.getEmail();
            int atIndex = originalEmail.indexOf('@');
            String newEmail;
            if (atIndex > 0) {
                String localPart = originalEmail.substring(0, atIndex);
                String domainPart = originalEmail.substring(atIndex);
                if (localPart.length() + timeSuffix.length() + domainPart.length() > 100) {
                    localPart = localPart.substring(0, 100 - timeSuffix.length() - domainPart.length());
                }
                newEmail = localPart + timeSuffix + domainPart;
            } else {
                newEmail = originalEmail + timeSuffix + "@deleted.local";
            }
            user.setEmail(newEmail);

            String newUsername = user.getActualUsername();
            if (newUsername.length() + timeSuffix.length() > 100) {
                newUsername = newUsername.substring(0, 100 - timeSuffix.length());
            }
            user.setUsername(newUsername + timeSuffix);

            userRepository.save(user);
            
            if (cacheManager != null && originalEmail != null) {
                org.springframework.cache.Cache profileCache = cacheManager.getCache(Constant.CACHE_USER_PROFILE);
                if (profileCache != null) {
                    profileCache.evict(originalEmail);
                }
            }
            
            log.info("User account deactivated: id={} by user={}", userId, currentUser.getActualUsername());

        } catch (SecurityException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deactivate user: id={}", userId, e);
            throw new ServiceException("Failed to deactivate user: " + e.getMessage(), e);
        }
    }

    // ===== Helper Methods =====

    private UserDetails buildUserDetails(User user) {
        return user;
    }

    private UserTagSuggestionDto convertToUserTagSuggestion(User user) {
        String activeTier = userPassRepository.findActivePassByUserId(user.getId())
                .map(pass -> pass.getTier().name())
                .orElse("GOVLYX_FREE");
        return UserTagSuggestionDto.builder()
                .id(user.getId())
                .username(user.getActualUsername())
                .displayName(user.getActualUsername())
                .profileImage(user.getProfileImage())
                .isActive(user.getIsActive())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .bio(PostUtility.truncateText(user.getBio(), Constant.POST_CONTENT_PREVIEW_LENGTH,
                        Constant.POST_CONTENT_TRUNCATION_SUFFIX))
                .taggableName("@" + user.getActualUsername())
                .pincode(user.getPincode())
                .totalTaggedPosts(userTagRepository.countByTaggedUser(user.getId()))
                .resolutionRate(calculateUserResolutionRate(user))
                .hasLocation(user.hasLocation())
                .tier(activeTier)
                .build();
    }

    private double calculateUserResolutionRate(User user) {
        try {
            if (!PostUtility.isDepartment(user)) {
                return 0.0;
            }

            long totalTagged = userTagRepository.countByTaggedUser(user.getId());
            long resolved = userTagRepository.countByTaggedUserAndPostStatus(user.getId(), PostStatus.RESOLVED);

            return PostUtility.calculateUserResolutionRate(user, totalTagged, resolved);
        } catch (Exception e) {
            log.warn("Failed to calculate resolution rate for user: {}", user.getActualUsername(), e);
            return 0.0;
        }
    }

    private void validateNewUser(User user) {
        if (user == null) {
            throw new ValidationException("User cannot be null");
        }
        if (user.getActualUsername() == null || user.getActualUsername().trim().isEmpty()) {
            throw new ValidationException("Username is required");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email is required");
        }
        if (user.getRole() == null) {
            throw new ValidationException("User role is required");
        }

        if (userRepository.findByUsername(user.getActualUsername()).isPresent()) {
            throw new ValidationException("Username already exists: " + user.getActualUsername());
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new ValidationException("Email already exists: " + user.getEmail());
        }

        if (user.getPincode() != null && !user.getPincode().trim().isEmpty()) {
            PostUtility.validateTargetPincodeForUser(user.getPincode().trim());
        }
    }

    public PaginatedResponse<User> searchUsersByRoleAndQuery(String roleName, String query, Long beforeId,
            Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "searchUsersByRoleAndQuery", beforeId, limit);

            String searchQuery = (query == null) ? "" : com.govlyx.AI.payload.PostUtility.sanitizeSqlLike(query.trim());

            List<User> users = userRepository.searchUsersByRoleAndQueryWithCursor(
                    roleName, searchQuery, setup.getSanitizedCursor(), setup.toPageable());

            return PaginationUtils.createUserResponse(users, setup.getValidatedLimit());
        } catch (Exception e) {
            log.error("Failed to search users for role '{}' and query '{}'", roleName, query, e);
            throw new ServiceException("Failed to search users: " + e.getMessage(), e);
        }
    }

    public long countTotalUsers() {
        try {
            return userRepository.count();
        } catch (Exception e) {
            log.error("Failed to count total users", e);
            throw new ServiceException("Could not retrieve user count.", e);
        }
    }

    public PaginatedResponse<UserTagSuggestionDto> searchUsers(String query, Long beforeId, Integer limit) {
        try {
            PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination(
                    "searchUsers", beforeId, limit,
                    Constant.DEFAULT_USER_SEARCH_LIMIT, Constant.MAX_USER_SEARCH_LIMIT);

            if (query == null || query.trim().length() < 2) {
                return PaginationUtils.createEmptyResponse(setup.getValidatedLimit());
            }

            Pageable pageable = PaginationUtils.createPageable(setup);

            String cleanQuery = com.govlyx.AI.payload.PostUtility.sanitizeSqlLike(query.trim());

            List<User> users;
            if (setup.hasCursor()) {
                users = userRepository.searchByUsernameWithCursor(cleanQuery,
                        setup.getSanitizedCursor(), pageable);
            } else {
                users = userRepository.searchByUsername(cleanQuery, pageable);
            }

            if (users == null) {
                users = List.of();
            }

            // Map to DTOs to avoid LazyInitializationException and keep response size small
            List<UserTagSuggestionDto> userDtos = users.stream()
                    .map(this::convertToUserTagSuggestion)
                    .collect(Collectors.toList());

            PaginatedResponse<UserTagSuggestionDto> response = PaginationUtils.createIdBasedResponse(
                    userDtos, setup.getValidatedLimit(), UserTagSuggestionDto::getId);

            PaginationUtils.logPaginationResults("searchUsers",
                    userDtos, response.isHasMore(), response.getNextCursor());

            return response;
        } catch (Exception e) {
            log.error("Failed to search users with query: {}", query, e);
            return PaginationUtils.handlePaginationError("searchUsers", e,
                    PaginationUtils.validateUserSearchLimit(limit));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public User verifyEmailUpdate(String token) {
        User user = userRepository.findByEmailUpdateToken(token)
                .orElseThrow(() -> new ValidationException("Invalid email update token."));

        if (user.getEmailUpdateTokenExpiry() != null && user.getEmailUpdateTokenExpiry().before(new java.util.Date())) {
            throw new ValidationException("Email update token has expired. Please request a new one.");
        }

        String newEmail = user.getPendingEmail();
        if (newEmail != null && !newEmail.isBlank()) {
            user.setEmail(newEmail);
            if (identityBlindService != null) {
                user.setEmailHash(identityBlindService.deriveEmailHash(newEmail));
            }
        }
        user.setPendingEmail(null);
        user.setEmailUpdateToken(null);
        user.setEmailUpdateTokenExpiry(null);
        user.setIsEmailVerified(true);
        User savedUser = userRepository.save(user);

        if (cacheManager != null) {
            org.springframework.cache.Cache authCache = cacheManager.getCache("authUserDetails");
            if (authCache != null) authCache.evict(savedUser.getId());

            org.springframework.cache.Cache profileCache = cacheManager.getCache(Constant.CACHE_USER_PROFILE);
            if (profileCache != null) {
                if (newEmail != null) profileCache.evict(newEmail);
                if (savedUser.getEmailHash() != null) profileCache.evict(savedUser.getEmailHash());
            }
        }

        return savedUser;
    }

    // ===== Session & Security =====

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "authUserDetails", key = "#userId")
    public String rotateSessionToken(Long userId) {
        try {
            User user = findById(userId);
            String newToken = java.util.UUID.randomUUID().toString();
            user.setSessionToken(newToken);
            userRepository.save(user);
            log.info("Session token rotated for user ID: {}", userId);
            return newToken;
        } catch (Exception e) {
            log.error("Failed to rotate session token for user ID: {}", userId, e);
            throw new ServiceException("Failed to rotate session token", e);
        }
    }
}
