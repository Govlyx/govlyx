package com.JanSahayak.AI.service;

import com.JanSahayak.AI.config.Constant;
import com.JanSahayak.AI.model.*;
import com.JanSahayak.AI.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * HyperlocalSeedService — updated to use real PincodeLookup table
 *
 * Uses your pincode_lookup columns directly:
 *   area_name → community name  (e.g. "Andheri East Community")
 *   city       → part of description / location name
 *   district   → community description and location
 *   state      → community description
 *   latitude   → stored on community (future geo-query support)
 *   longitude  → stored on community (future geo-query support)
 *   is_active  → only seed from active pincode entries
 *
 * Flow:
 *   UserService.registerUser(user)
 *       → hyperlocalSeedService.onUserRegistered(user)
 *           → check if seeded community exists for user's pincode
 *               YES → auto-enroll user + done
 *               NO  → count active users by pincode
 *                       count >= Constant.HYPERLOCAL_SEED_THRESHOLD (5)
 *                           → look up real location from pincode_lookup
 *                           → create community with real names
 *                           → bulk-enroll all existing users in that pincode
 *
 * Community naming strategy (using real PincodeLookup data):
 *   Primary  : "{area_name} Community"          → "Andheri East Community"
 *   Fallback : "{city} - {area_name} Community" → "Mumbai - Andheri East Community"
 *   Last     : "Pincode {pincode} Community"    → "Pincode 400069 Community"
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HyperlocalSeedService {

    private final CommunityRepo       communityRepo;
    private final CommunityMemberRepo memberRepo;
    private final UserRepo            userRepo;
    private final PincodeLookupRepo   pincodeRepo;   // ← real PincodeLookup table

    /** Minimum active users in a pincode before auto-creating a community. */

    // =========================================================================
    // Entry Point
    // =========================================================================

    /**
     * Called by UserService.registerUser() immediately after saving a new user.
     *
     * Guards:
     *   - User must have a valid 6-digit Indian pincode
     *   - Pincode must exist in pincode_lookup AND be active
     *   - Race condition: double-check before seeding (two concurrent registrations
     *     could both hit threshold simultaneously)
     */
    public void onUserRegistered(User newUser) {
        if (newUser == null || !Constant.isValidIndianPincode(newUser.getPincode())) {
            return;
        }

        String pincode = newUser.getPincode();

        // Guard: pincode must be known and active in our lookup table
        if (!pincodeRepo.existsByPincodeAndIsActiveTrue(pincode)) {
            log.debug("Pincode {} not found in pincode_lookup or inactive — skipping hyperlocal seed", pincode);
            return;
        }

        // If community already exists → just enroll this new user
        if (communityRepo.existsByPincodeAndIsSystemSeededTrue(pincode)) {
            communityRepo.findByPincodeAndIsSystemSeededTrue(pincode).ifPresent(community -> {
                autoEnrollUser(community, newUser);
                communityRepo.incrementSeedTriggerCount(pincode);
            });
            return;
        }

        // FIX #7: Was calling communityRepo.countActiveUsersByPincode() which does not
        // exist on CommunityRepo — this caused a runtime NoSuchMethodError that silently
        // swallowed the seed trigger. Must call userRepo.countActiveUsersByPincode()
        // to correctly count active users registered at this pincode.
        long count = userRepo.countActiveUsersByPincode(pincode);
        if (count < Constant.HYPERLOCAL_SEED_THRESHOLD) {
            log.debug("Pincode {} has {} users — below threshold {} for seeding",
                    pincode, count, Constant.HYPERLOCAL_SEED_THRESHOLD);
            return;
        }

        // Race-condition guard: another thread may have seeded between our checks
        if (communityRepo.existsByPincodeAndIsSystemSeededTrue(pincode)) {
            communityRepo.findByPincodeAndIsSystemSeededTrue(pincode)
                    .ifPresent(c -> autoEnrollUser(c, newUser));
            return;
        }

        seedCommunityForPincode(pincode, newUser);
    }

    // =========================================================================
    // Seeding
    // =========================================================================

    private void seedCommunityForPincode(String pincode, User triggerUser) {
        log.info("Seeding hyperlocal community for pincode {} triggered by user {}",
                pincode, triggerUser.getId());

        // Load real location data from pincode_lookup
        Optional<PincodeLookup> lookupOpt = pincodeRepo.findByPincodeAndIsActiveTrue(pincode);
        PincodeLookup location = lookupOpt.orElse(null);

        // Build community metadata from real PincodeLookup data
        CommunityNames names   = buildCommunityNames(pincode, location);
        CommunityGeo   geo     = buildGeo(pincode, location);

        // Find system admin to be nominal owner (uses Constant.ROLE_ADMIN from your Constant.java)
        User systemOwner = userRepo.findFirstByRoleName(Constant.ROLE_ADMIN)
                .orElse(triggerUser);

        Community community = Community.builder()
                .name(uniqueName(names.communityName))
                .description(names.description)
                .category("LOCAL")
                .privacy(Community.CommunityPrivacy.PUBLIC)
                .feedEligible(true)
                // Location — from pincode_lookup real data
                .pincode(pincode)
                .statePrefix(Constant.getStatePrefixFromPincode(pincode))
                .districtPrefix(Constant.getDistrictPrefixFromPincode(pincode))
                .locationName(geo.locationName)
                // Hyperlocal seed metadata
                .locationRestricted(true)
                .isSystemSeeded(true)
                .wardName(names.wardName)
                .seedThreshold(Constant.HYPERLOCAL_SEED_THRESHOLD)
                .seedTriggerCount(1)
                .owner(systemOwner)
                .memberCount(0)
                .build();

        communityRepo.save(community);

        // Bulk-enroll all existing active users in this pincode (in batches)
        enrollAllUsersForPincode(community, pincode);

        log.info("Hyperlocal community '{}' (id={}) created for pincode {} — {} members, location: {}",
                community.getName(), community.getId(), pincode,
                community.getMemberCount(), geo.locationName);
    }

    // =========================================================================
    // Bulk Enrollment
    // =========================================================================

    private void enrollAllUsersForPincode(Community community, String pincode) {
        int page     = 0;
        int enrolled = 0;
        List<User> batch;

        do {
            batch = userRepo.findActiveUsersByPincode(pincode, PageRequest.of(page, Constant.HYPERLOCAL_ENROLL_BATCH));
            for (User user : batch) {
                if (!memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(community.getId(), user.getId())) {
                    autoEnrollUser(community, user);
                    enrolled++;
                }
            }
            page++;
        } while (batch.size() == Constant.HYPERLOCAL_ENROLL_BATCH);

        log.info("Enrolled {} existing users into community {} (pincode {})",
                enrolled, community.getId(), pincode);
    }

    private void autoEnrollUser(Community community, User user) {
        if (memberRepo.existsByCommunityIdAndUserIdAndIsActiveTrue(community.getId(), user.getId())) {
            return;
        }
        memberRepo.save(CommunityMember.builder()
                .community(community)
                .user(user)
                .memberRole(CommunityMember.MemberRole.MEMBER)
                .build());
        communityRepo.incrementMemberCount(community.getId());
        log.debug("Auto-enrolled user {} into community {}", user.getId(), community.getId());
    }

    // =========================================================================
    // Location Resolution (uses real PincodeLookup columns)
    // =========================================================================

    /**
     * Builds human-readable community names from real PincodeLookup data.
     *
     * Uses these columns:
     *   area_name  → primary neighbourhood name (e.g. "Andheri East")
     *   city       → city name (e.g. "Mumbai")
     *   district   → district name (e.g. "Mumbai Suburban")
     *   state      → state name (e.g. "Maharashtra")
     *
     * Priority:
     *   1. area_name is distinct enough → "{area_name} Community"
     *   2. area_name is generic (< 8 chars) → "{area_name}, {city} Community"
     *   3. No lookup data → "Pincode {pincode} Community"
     */
    private CommunityNames buildCommunityNames(String pincode, PincodeLookup location) {
        if (location == null) {
            // Fallback: no lookup data
            return new CommunityNames(
                    "Pincode " + pincode + " Community",
                    "Pincode " + pincode,
                    "Local community for residents of pincode " + pincode + "."
            );
        }

        String areaName = location.getAreaName();   // e.g. "Andheri East"
        String city     = location.getCity();        // e.g. "Mumbai"
        String district = location.getDistrict();    // e.g. "Mumbai Suburban"
        String state    = location.getState();       // e.g. "Maharashtra"

        // FIX: areaName can be null if the pincode_lookup row has no area_name value.
        // Fall back through city → district → pincode so we always have a non-null name.
        if (areaName == null || areaName.isBlank()) {
            if (city != null && !city.isBlank())         areaName = city;
            else if (district != null && !district.isBlank()) areaName = district;
            else                                              areaName = "Pincode " + pincode;
        }

        // Ward name = area_name (what we display on the community badge)
        String wardName = areaName;

        // Community name: "Andheri East Community"
        // If area_name is very short/generic, prefix with city for clarity
        String communityName;
        if (areaName.length() >= 8) {
            communityName = areaName + " Community";
        } else if (city != null && !city.isBlank()) {
            communityName = areaName + ", " + city + " Community";
        } else {
            communityName = areaName + " Community";
        }

        // Description uses PincodeLookup.getDisplayLocation() helper from your entity:
        // e.g. "Andheri East (400069), Mumbai Suburban, Maharashtra"
        // FIX: getDisplayLocation() may return null — fall back to areaName so
        // String.format never receives a null argument (which would print "null").
        String displayLocation = location.getDisplayLocation();
        if (displayLocation == null || displayLocation.isBlank()) {
            displayLocation = areaName + " (" + pincode + ")";
        }
        String description = String.format(
                "Hyperlocal community for residents of %s. " +
                        "Connect with your neighbours, share local updates, and discuss issues in your area.",
                displayLocation
        );

        return new CommunityNames(communityName, wardName, description);
    }

    /**
     * Builds geo/location fields for the Community entity.
     *
     * Uses these columns:
     *   area_name, city, district, state → locationName (human-readable display)
     *   latitude, longitude              → stored for future geo-queries
     *
     * Uses PincodeLookup helper methods from your entity:
     *   getFullLocationName()  → "Andheri East, Mumbai, Mumbai Suburban, Maharashtra"
     *   getLocationName()      → "Mumbai Suburban Maharashtra" (backward compat)
     */
    private CommunityGeo buildGeo(String pincode, PincodeLookup location) {
        if (location == null) {
            return new CommunityGeo("Pincode " + pincode, null, null);
        }

        // locationName displayed on community cards — uses your entity's helper
        // getFullLocationName() → "Andheri East, Mumbai, Mumbai Suburban, Maharashtra"
        String locationName = location.getFullLocationName();

        // latitude/longitude from pincode_lookup — null if not available
        String lat = location.hasCoordinates() ? location.getLatitude().toPlainString()  : null;
        String lon = location.hasCoordinates() ? location.getLongitude().toPlainString() : null;

        return new CommunityGeo(locationName, lat, lon);
    }

    /**
     * Ensures the community name is unique by appending a numeric suffix if needed.
     * e.g. "Andheri East Community" → "Andheri East Community 2"
     */
    private String uniqueName(String baseName) {
        String candidate = baseName;
        int suffix = 2;
        while (communityRepo.existsByName(candidate)) {
            candidate = baseName + " " + suffix++;
        }
        return candidate;
    }

    // =========================================================================
    // Public Utility — called by CommunityService.createCommunity()
    // =========================================================================

    /**
     * Resolves real location display name for a given pincode.
     * Used by CommunityService when a user creates a location-restricted community —
     * instead of leaving locationName blank, we auto-fill it from the lookup table.
     *
     * Returns null if pincode is not found in lookup table (caller can leave locationName blank).
     */
    public LocationData resolveLocationData(String pincode) {
        if (!Constant.isValidIndianPincode(pincode)) return null;
        return pincodeRepo.findByPincodeAndIsActiveTrue(pincode)
                .map(loc -> new LocationData(
                        loc.getAreaName(),
                        loc.getCity(),
                        loc.getDistrict(),
                        loc.getState(),
                        loc.getFullLocationName(),
                        loc.getDisplayLocation(),
                        loc.hasCoordinates() ? loc.getLatitude().toPlainString()  : null,
                        loc.hasCoordinates() ? loc.getLongitude().toPlainString() : null
                ))
                .orElse(null);
    }

    // =========================================================================
    // Internal value holders
    // =========================================================================

    private record CommunityNames(String communityName, String wardName, String description) {}
    private record CommunityGeo(String locationName, String latitude, String longitude) {}

    /**
     * Exposed to callers (e.g. CommunityService) so they can use the same
     * PincodeLookup data when auto-filling community location fields.
     */
    public record LocationData(
            String areaName,
            String city,
            String district,
            String state,
            String fullLocationName,
            String displayLocation,
            String latitude,
            String longitude
    ) {
        /**
         * Builds a short location label for community cards.
         * e.g. "Andheri East, Mumbai"  or  "Andheri East, Mumbai Suburban"
         */
        public String shortLabel() {
            if (city != null && !city.isBlank()) {
                return areaName + ", " + city;
            }
            return areaName + ", " + district;
        }
    }
}
