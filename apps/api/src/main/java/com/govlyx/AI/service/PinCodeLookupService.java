package com.govlyx.AI.service;

import com.govlyx.AI.dto.PaginatedResponse;
import com.govlyx.AI.config.Constant;
import com.govlyx.AI.model.PincodeLookup;
import com.govlyx.AI.model.User;
import com.govlyx.AI.repository.PincodeLookupRepo;
import com.govlyx.AI.payload.PaginationUtils;
import com.govlyx.AI.payload.PostUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinCodeLookupService {

    private final PincodeLookupRepo pincodeLookupRepository;

    private PinCodeLookupService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy PinCodeLookupService self) {
        this.self = self;
    }

    // ===== Basic CRUD Operations =====

    @Cacheable(value = Constant.CACHE_PINCODE_DATA, key = "#pincode", condition = "#pincode != null", unless = "#result == null")
    public Optional<PincodeLookup> findByPincode(String pincode) {
        if (!Constant.isValidIndianPincode(pincode)) {
            return Optional.empty();
        }
        return pincodeLookupRepository.findById(pincode);
    }

    public PaginatedResponse<PincodeLookup> findAll(String beforePincode, Integer limit) {
        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findAll",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding all pincodes with cursor: {}, limit: {}", beforePincode, setup.getValidatedLimit());

        // FIX: Use DB-level cursor pagination instead of loading all rows into heap.
        List<PincodeLookup> pincodes = pincodeLookupRepository.findCursorPage(
                beforePincode,
                PageRequest.of(0, (int) setup.getValidatedLimit())
        );

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findActiveOnly(String beforePincode, Integer limit) {
        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findActiveOnly",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding active pincodes with cursor: {}, limit: {}", beforePincode, setup.getValidatedLimit());

        // FIX: Use paginated DB query, then stream-filter for isActive = true (active pincode share is high).
        List<PincodeLookup> pincodes = pincodeLookupRepository
                .findByIsActiveTrue(PageRequest.of(0, (int) setup.getValidatedLimit()))
                .stream()
                .filter(p -> beforePincode == null || p.getPincode().compareTo(beforePincode) < 0)
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Search Operations =====

    public PaginatedResponse<PincodeLookup> searchByAreaName(String areaName, String beforePincode, Integer limit) {
        if (areaName == null || areaName.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchByAreaName",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = areaName.trim();

        log.debug("Searching by area name: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository
                    .findByAreaNameContainingIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit() * 2))
                    .stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository
                    .findByAreaNameContainingIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> searchByCity(String city, String beforePincode, Integer limit) {
        if (city == null || city.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchByCity",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = city.trim();

        log.debug("Searching by city: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository
                    .findByCityContainingIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit() * 2))
                    .stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository
                    .findByCityContainingIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByDistrict(String district, String beforePincode, Integer limit) {
        if (district == null || district.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByDistrict",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = district.trim();

        log.debug("Finding by district: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository
                    .findByDistrictIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit() * 2))
                    .stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository
                    .findByDistrictIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByState(String state, String beforePincode, Integer limit) {
        if (state == null || state.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByState",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = state.trim();

        log.debug("Finding by state: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository
                    .findByStateIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit() * 2))
                    .stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository
                    .findByStateIgnoreCaseAndIsActiveTrue(searchTerm,
                            PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> searchGeneral(String query, String beforePincode, Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("searchGeneral",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String searchTerm = query.trim();

        log.debug("General search for: '{}', cursor: {}, limit: {}", searchTerm, beforePincode, setup.getValidatedLimit());

        // If it's a 6-digit pincode, search by pincode first
        if (Constant.isValidIndianPincode(searchTerm)) {
            Optional<PincodeLookup> byPincode = findByPincode(searchTerm);
            if (byPincode.isPresent()) {
                List<PincodeLookup> result = List.of(byPincode.get());
                return PaginationUtils.createCustomResponse(result, false, null, setup.getValidatedLimit());
            }
        }

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findBySearchableTextContainingIgnoreCaseAndIsActiveTrue(searchTerm);
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findBySearchableTextContainingIgnoreCaseAndIsActiveTrue(searchTerm)
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Validation Operations =====

    public boolean isValidPincode(String pincode) {
        if (!Constant.isValidIndianPincode(pincode)) {
            return false;
        }
        return pincodeLookupRepository.existsByPincodeAndIsActiveTrue(pincode);
    }

    public boolean isPincodeActive(String pincode) {
        return findByPincode(pincode)
                .map(PincodeLookup::getIsActive)
                .orElse(false);
    }

    // ===== Geographic Operations (Using Pincode Prefix Logic) =====

    public PaginatedResponse<PincodeLookup> findNearbyPincodes(String pincode, double radiusKm, String beforePincode, Integer limit) {
        Optional<PincodeLookup> centerPincode = findByPincode(pincode);
        if (centerPincode.isEmpty() || !centerPincode.get().hasCoordinates()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findNearbyPincodes",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        PincodeLookup center = centerPincode.get();

        log.debug("Finding nearby pincodes for: {}, radius: {}km, cursor: {}, limit: {}",
                pincode, radiusKm, beforePincode, setup.getValidatedLimit());

        // FIX MEMORY LEAK #4 — previously called findActiveOnly(null, Integer.MAX_VALUE)
        // which loaded the ENTIRE pincode_lookup table into heap for in-memory filtering.
        // Now we use the district prefix (first 3 digits) to narrow the DB query to a
        // geographically relevant slice before running the radius filter.
        // This reduces heap usage from O(ALL_PINCODES) to O(DISTRICT_PINCODES).
        String districtPrefix = pincode.length() >= 3 ? pincode.substring(0, 3) : pincode;
        List<PincodeLookup> candidates = pincodeLookupRepository
                .findByPincodeStartingWithAndIsActiveTrue(districtPrefix, PageRequest.of(0, 500));

        // If the district slice is too small (rural area) widen to state prefix
        if (candidates.size() < setup.getValidatedLimit()) {
            String statePrefix = pincode.length() >= 2 ? pincode.substring(0, 2) : pincode;
            candidates = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(statePrefix, PageRequest.of(0, 2000));
        }

        List<PincodeLookup> nearbyPincodes = candidates.stream()
                .filter(p -> p.hasCoordinates() && !p.getPincode().equals(pincode))
                .filter(p -> center.calculateDistanceTo(p) <= radiusKm)
                .filter(p -> beforePincode == null || p.getPincode().compareTo(beforePincode) < 0)
                .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                .limit(setup.getValidatedLimit())
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(nearbyPincodes, setup.getValidatedLimit(),
                pincodeObj -> Long.valueOf(pincodeObj.getPincode()));
    }

    /**
     * Specialized version of nearby search for matchmaking.
     * Returns a flat Set of 6-digit pincode strings within the radius.
     * Uses a fixed 20km radius and caps at 100 results for speed.
     */
    public java.util.Set<String> getNearbyPincodeStrings(String pincode) {
        return getNearbyPincodeStrings(pincode, 20.0, 100);
    }

    /**
     * Specialized version of nearby search for matchmaking with custom radius.
     */
    @Cacheable(value = "pincode_nearby", key = "#pincode + '_' + #radiusKm + '_' + #limit", condition = "#pincode != null", unless = "#result == null or #result.isEmpty()")
    public java.util.Set<String> getNearbyPincodeStrings(String pincode, double radiusKm, int limit) {
        if (pincode == null) return java.util.Collections.emptySet();
        
        List<PincodeLookup> nearby = findNearbyPincodes(pincode, radiusKm, null, limit).getData();
        
        return nearby.stream()
                .map(PincodeLookup::getPincode)
                .collect(Collectors.toSet());
    }

    public PaginatedResponse<PincodeLookup> findByStateAndDistrict(String state, String district, String beforePincode, Integer limit) {
        if (state == null || district == null) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByStateAndDistrict",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);
        String stateSearch = state.trim();
        String districtSearch = district.trim();

        log.debug("Finding by state: '{}' and district: '{}', cursor: {}, limit: {}",
                stateSearch, districtSearch, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository
                    .findByStateIgnoreCaseAndDistrictIgnoreCaseAndIsActiveTrue(stateSearch, districtSearch,
                            PageRequest.of(0, (int) setup.getValidatedLimit() * 2))
                    .stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository
                    .findByStateIgnoreCaseAndDistrictIgnoreCaseAndIsActiveTrue(stateSearch, districtSearch,
                            PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Pincode Prefix-Based Geographic Operations =====

    public PaginatedResponse<PincodeLookup> findByStatePrefix(String statePrefix, String beforePincode, Integer limit) {
        if (!Constant.isValidIndianStatePrefix(statePrefix)) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByStatePrefix",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding by state prefix: '{}', cursor: {}, limit: {}", statePrefix, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(statePrefix, PageRequest.of(0, (int) setup.getValidatedLimit() * 2));
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(statePrefix, PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByDistrictPrefix(String districtPrefix, String beforePincode, Integer limit) {
        if (districtPrefix == null || !districtPrefix.matches(Constant.INDIAN_DISTRICT_PREFIX_PATTERN)) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByDistrictPrefix",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding by district prefix: '{}', cursor: {}, limit: {}", districtPrefix, beforePincode, setup.getValidatedLimit());

        List<PincodeLookup> pincodes;
        if (beforePincode != null) {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(districtPrefix, PageRequest.of(0, (int) setup.getValidatedLimit() * 2));
            pincodes = pincodes.stream()
                    .filter(p -> p.getPincode().compareTo(beforePincode) < 0)
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        } else {
            pincodes = pincodeLookupRepository.findByPincodeStartingWithAndIsActiveTrue(districtPrefix, PageRequest.of(0, (int) setup.getValidatedLimit()))
                    .stream()
                    .sorted((p1, p2) -> p2.getPincode().compareTo(p1.getPincode()))
                    .limit(setup.getValidatedLimit())
                    .collect(Collectors.toList());
        }

        return PaginationUtils.createIdBasedResponse(pincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    public PaginatedResponse<PincodeLookup> findByPincodePrefixes(List<String> prefixes, String beforePincode, Integer limit) {
        if (prefixes == null || prefixes.isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        PaginationUtils.PaginationSetup setup = PaginationUtils.setupPagination("findByPincodePrefixes",
                beforePincode != null ? Long.valueOf(beforePincode) : null, limit);

        log.debug("Finding by pincode prefixes: {}, cursor: {}, limit: {}", prefixes, beforePincode, setup.getValidatedLimit());

        // FIX: Issue a single DB query with OR-prefix matching instead of N sequential calls.
        // Take up to 3 distinct prefixes (degenerate input guard).
        List<String> validPrefixes = prefixes.stream()
                .filter(p -> p != null && p.matches("\\d{2,6}"))
                .distinct()
                .limit(3)
                .collect(Collectors.toList());

        if (validPrefixes.isEmpty()) {
            return PaginationUtils.createCustomResponse(List.of(), false, null, 0);
        }

        // Pad to exactly 3 params (findByAnyPrefixAndIsActiveTrue always needs 3)
        String p1 = validPrefixes.get(0);
        String p2 = validPrefixes.size() > 1 ? validPrefixes.get(1) : null;
        String p3 = validPrefixes.size() > 2 ? validPrefixes.get(2) : null;

        List<PincodeLookup> allPincodes = pincodeLookupRepository.findByAnyPrefixAndIsActiveTrue(
                p1, p2, p3,
                PageRequest.of(0, (int) setup.getValidatedLimit()))
                .stream()
                .filter(p -> beforePincode == null || p.getPincode().compareTo(beforePincode) < 0)
                .collect(Collectors.toList());

        return PaginationUtils.createIdBasedResponse(allPincodes, setup.getValidatedLimit(),
                pincode -> Long.valueOf(pincode.getPincode()));
    }

    // ===== Statistics and Analytics =====
    public PincodeStatsDto getStatistics() {
        // FIX MEMORY LEAK #5 (final fix) — replaced findAll() + Java-stream aggregation
        // with native DB COUNT and GROUP BY queries. Zero rows are loaded into Java heap;
        // all math happens inside PostgreSQL.
        long activeCount   = pincodeLookupRepository.countActivePincodes();
        long inactiveCount = pincodeLookupRepository.countInactivePincodes();
        long totalCount    = activeCount + inactiveCount;

        // Build state map from DB GROUP BY
        List<Object[]> byStateRows = pincodeLookupRepository.countActivePincodesByState();
        Map<String, Long> byState = new LinkedHashMap<>();
        for (Object[] row : byStateRows) {
            byState.put((String) row[0], (Long) row[1]);
        }

        // Build district map from DB GROUP BY (key: "State - District")
        List<Object[]> byDistrictRows = pincodeLookupRepository.countActivePincodesByDistrict();
        Map<String, Long> byDistrict = new LinkedHashMap<>();
        for (Object[] row : byDistrictRows) {
            String key = row[0] + " - " + row[1];
            byDistrict.put(key, (Long) row[2]);
        }

        return PincodeStatsDto.builder()
                .totalPincodes(totalCount)
                .activePincodes(activeCount)
                .inactivePincodes(inactiveCount)
                .pincodesByState(byState)
                .pincodesByDistrict(byDistrict)
                .totalStates((long) byState.size())
                .totalDistricts((long) byDistrict.size())
                .build();
    }


    /**
     * All active states — cached 24 h.
     * Called on every broadcast-creation form and Settings page.
     * State names virtually never change. No @CacheEvict needed.
     */
    @Cacheable(value = Constant.CACHE_PINCODE_DATA, key = "'all-states'")
    public List<String> getAllStates() {
        // Note: State/district lists don't need pagination as they're typically small
        return pincodeLookupRepository.findDistinctStatesByIsActiveTrue();
    }

    /**
     * All active districts — cached 24 h.
     */
    @Cacheable(value = Constant.CACHE_PINCODE_DATA, key = "'all-districts'")
    public List<String> getAllDistricts() {
        return pincodeLookupRepository.findDistinctDistrictsByIsActiveTrue();
    }

    /**
     * Districts for a given state — cached 24 h.
     * Called each time the user selects a state in broadcast targeting.
     */
    @Cacheable(value = Constant.CACHE_PINCODE_DATA, key = "'districts-' + #state.toLowerCase()", condition = "#state != null")
    public List<String> getDistrictsByState(String state) {
        if (state == null || state.trim().isEmpty()) {
            return List.of();
        }
        return pincodeLookupRepository.findDistinctDistrictsByStateIgnoreCaseAndIsActiveTrue(state.trim());
    }
    public List<String> getDistrictsByStates(List<String> states) {
        // ... returns ALL districts at once
        return states.stream()
                .flatMap(state -> self.getDistrictsByState(state).stream()) // Gets ALL districts
                .distinct()
                .sorted()
                .collect(Collectors.toList()); // Returns complete list
    }

    // ===== User Helper Methods (Using Pincode Prefix Logic) =====

    public void populateUserLocationData(User user) {
        PostUtility.validateUser(user);

        if (!user.hasPincode()) {
            return;
        }

        findByPincode(user.getPincode()).ifPresent(user::setPincodeLookupData);
    }

    public String getUserFullLocation(User user) {
        if (user == null || user.getPincodeLookupData() == null) {
            return user != null && user.hasPincode() ? user.getPincode() : null;
        }

        return user.getPincodeLookupData().getFullLocationName();
    }

    public String getUserDisplayLocation(User user) {
        if (user == null || user.getPincodeLookupData() == null) {
            return user != null && user.hasPincode() ? user.getPincode() : null;
        }

        return user.getPincodeLookupData().getDisplayLocation();
    }

    // ===== Pincode Prefix Conversion Methods =====

    public List<String> convertStatesToPrefixes(List<String> stateNames) {
        return PostUtility.convertStatesToPincodePrefixes(stateNames, this);
    }

    public List<String> convertDistrictsToPrefixes(List<String> districtNames) {
        return PostUtility.convertDistrictsToPincodePrefixes(districtNames, this);
    }

    // ===== Backward Compatibility Methods (Non-paginated) =====
    // These methods maintain the original signatures for existing code that doesn't need pagination

    public List<PincodeLookup> findByDistrict(String district) {
        return findByDistrict(district, null, 50).getData();
    }

    public List<PincodeLookup> searchByAreaName(String areaName) {
        return searchByAreaName(areaName, null, 50).getData();
    }

    public List<PincodeLookup> searchByCity(String city) {
        return searchByCity(city, null, 50).getData();
    }

    public List<PincodeLookup> findByState(String state) {
        return findByState(state, null, 50).getData();
    }

    public List<PincodeLookup> searchGeneral(String query) {
        return searchGeneral(query, null, 50).getData();
    }

    public List<PincodeLookup> findNearbyPincodes(String pincode, double radiusKm) {
        return findNearbyPincodes(pincode, radiusKm, null, 100).getData();
    }

    public Optional<PincodeLookup> findClosestPincode(double lat, double lng) {
        return pincodeLookupRepository.findClosestPincode(lat, lng);
    }

    public List<PincodeLookup> findByStateAndDistrict(String state, String district) {
        return findByStateAndDistrict(state, district, null, 50).getData();
    }

    public List<PincodeLookup> findByStatePrefix(String statePrefix) {
        return findByStatePrefix(statePrefix, null, 50).getData();
    }

    public List<PincodeLookup> findByDistrictPrefix(String districtPrefix) {
        return findByDistrictPrefix(districtPrefix, null, 50).getData();
    }

    public List<PincodeLookup> findByPincodePrefixes(List<String> prefixes) {
        return findByPincodePrefixes(prefixes, null, 50).getData();
    }

    public List<String> getDistinctPrefixesByState(String state, int prefixLength) {
        if (state == null || state.trim().isEmpty()) return List.of();
        return pincodeLookupRepository.findDistinctPrefixesByStateIgnoreCase(state.trim(), prefixLength);
    }

    public List<String> getDistinctPrefixesByDistrict(String district, int prefixLength) {
        if (district == null || district.trim().isEmpty()) return List.of();
        return pincodeLookupRepository.findDistinctPrefixesByDistrictIgnoreCase(district.trim(), prefixLength);
    }

    public String getDistrictNameByPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) return null;
        List<PincodeLookup> matches = pincodeLookupRepository.findCursorPage(prefix, PageRequest.of(0, 1));
        if (matches != null && !matches.isEmpty()) {
            PincodeLookup p = matches.get(0);
            if (p.getPincode().startsWith(prefix)) {
                return p.getDistrict();
            }
        }
        return null;
    }

    public String getStateNameByPrefix(String prefix) {
        if (prefix == null || prefix.length() < 2) return null;
        return PostUtility.getStateFromPincodePrefix(prefix);
    }

    // ===== Private Helper Methods =====

    private void validateAdminPermission(User user) {
        PostUtility.validateUser(user);

        if (!PostUtility.isAdmin(user)) {
            throw new SecurityException("Only administrators can perform this operation");
        }
    }

    // ===== DTO Classes =====

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PincodeStatsDto {
        private Long totalPincodes;
        private Long activePincodes;
        private Long inactivePincodes;
        private Map<String, Long> pincodesByState;
        private Map<String, Long> pincodesByDistrict;
        private Long totalStates;
        private Long totalDistricts;
    }
}
