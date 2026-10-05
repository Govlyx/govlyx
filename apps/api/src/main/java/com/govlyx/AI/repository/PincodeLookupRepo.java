package com.govlyx.AI.repository;

import com.govlyx.AI.model.PincodeLookup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PincodeLookupRepo
 *
 * Unified repository for the pincode_lookup table.
 * Columns: pincode (PK), area_name, city, district, state,
 *          latitude, longitude, is_active, created_at, updated_at
 *
 * Used by:
 *   HyperlocalSeedService  — resolveLocationData(pincode) to build community name/location
 *   CommunityService       — enrichCommunityLocation(community) on create/update
 *   Any service needing      real area/district/state names for display
 */
@Repository
public interface PincodeLookupRepo extends JpaRepository<PincodeLookup, String> {

    // =========================================================================
    // PRIMARY LOOKUP
    // =========================================================================

    /**
     * Find active pincode entry. Main method used by HyperlocalSeedService.
     * Returns empty if pincode is unknown or deactivated.
     */
    Optional<PincodeLookup> findByPincodeAndIsActiveTrue(String pincode);

    /** Check if a pincode exists and is active — cheaper than loading the full entity. */
    boolean existsByPincodeAndIsActiveTrue(String pincode);

    // =========================================================================
    // ACTIVE / INACTIVE
    // =========================================================================

    List<PincodeLookup> findByIsActiveTrue(Pageable pageable);

    List<PincodeLookup> findByIsActiveFalse(Pageable pageable);

    // =========================================================================
    // AREA NAME
    // =========================================================================

    List<PincodeLookup> findByAreaNameContainingIgnoreCaseAndIsActiveTrue(String areaName, Pageable pageable);

    // =========================================================================
    // CITY
    // =========================================================================

    /** Exact city match (case-insensitive), active only. */
    List<PincodeLookup> findByCityAndIsActiveTrueOrderByAreaNameAsc(String city, Pageable pageable);

    /** Partial city match (case-insensitive), active only. */
    List<PincodeLookup> findByCityContainingIgnoreCaseAndIsActiveTrue(String city, Pageable pageable);

    // =========================================================================
    // DISTRICT
    // =========================================================================

    /** Exact district match (case-insensitive), active only. */
    List<PincodeLookup> findByDistrictIgnoreCaseAndIsActiveTrue(String district, Pageable pageable);

    /**
     * Exact district match ordered by area name.
     * Used to build district-level community seeding.
     */
    List<PincodeLookup> findByDistrictAndIsActiveTrueOrderByAreaNameAsc(String district, Pageable pageable);

    /** Count distinct active pincodes in a district. */
    @Query("SELECT COUNT(p) FROM PincodeLookup p WHERE p.district = :district AND p.isActive = true")
    long countActiveByDistrict(@Param("district") String district);

    // =========================================================================
    // STATE
    // =========================================================================

    /** Exact state match (case-insensitive), active only. */
    List<PincodeLookup> findByStateIgnoreCaseAndIsActiveTrue(String state, Pageable pageable);

    /** Exact state match ordered by district then area name. */
    List<PincodeLookup> findByStateAndIsActiveTrueOrderByDistrictAscAreaNameAsc(String state, Pageable pageable);

    /** Count distinct active pincodes in a state. */
    @Query("SELECT COUNT(p) FROM PincodeLookup p WHERE p.state = :state AND p.isActive = true")
    long countActiveByState(@Param("state") String state);

    // =========================================================================
    // COMBINED STATE + DISTRICT
    // =========================================================================

    List<PincodeLookup> findByStateIgnoreCaseAndDistrictIgnoreCaseAndIsActiveTrue(String state, String district, Pageable pageable);

    // =========================================================================
    // PINCODE PREFIX
    // =========================================================================

    /**
     * Find pincodes starting with a given prefix.
     * Used for state/district prefix-based community scoping.
     */
    List<PincodeLookup> findByPincodeStartingWithAndIsActiveTrue(String prefix, Pageable pageable);

    /**
     * Multi-prefix OR batch query — replaces N sequential prefix calls in findByPincodePrefixes().
     * Filters active pincodes that start with any of the provided prefixes in one round-trip.
     */
    @Query("""
            SELECT p FROM PincodeLookup p
            WHERE p.isActive = true
              AND (
                 (:prefix1 IS NOT NULL AND p.pincode LIKE CONCAT(:prefix1, '%')) OR
                 (:prefix2 IS NOT NULL AND p.pincode LIKE CONCAT(:prefix2, '%')) OR
                 (:prefix3 IS NOT NULL AND p.pincode LIKE CONCAT(:prefix3, '%'))
              )
            ORDER BY p.pincode DESC
            """)
    List<PincodeLookup> findByAnyPrefixAndIsActiveTrue(
            @Param("prefix1") String prefix1,
            @Param("prefix2") String prefix2,
            @Param("prefix3") String prefix3,
            Pageable pageable);

    // =========================================================================
    // GENERAL TEXT SEARCH
    // =========================================================================

    /**
     * Full-text style search across pincode, area_name, city, district, state.
     * Used for registration pincode autocomplete and admin lookups.
     */
    @Query("""
            SELECT p FROM PincodeLookup p
            WHERE p.isActive = true
              AND (
                  LOWER(p.pincode)   LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.areaName)  LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.city)      LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.district)  LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.state)     LIKE LOWER(CONCAT('%',:term,'%'))
              )
            ORDER BY p.areaName ASC
            """)
    List<PincodeLookup> searchByTerm(@Param("term") String term);

    /**
     * Backward-compatibility alias for searchByTerm().
     * Keeps existing service call sites working without modification.
     */
    @Query("""
            SELECT p FROM PincodeLookup p
            WHERE p.isActive = true
              AND (
                  LOWER(p.pincode)   LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.areaName)  LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.city)      LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.district)  LIKE LOWER(CONCAT('%',:term,'%'))
               OR LOWER(p.state)     LIKE LOWER(CONCAT('%',:term,'%'))
              )
            ORDER BY p.areaName ASC
            """)
    List<PincodeLookup> findBySearchableTextContainingIgnoreCaseAndIsActiveTrue(@Param("term") String term);

    // =========================================================================
    // COORDINATE-BASED (for future distance-aware features)
    // =========================================================================

    @Query("""
            SELECT p FROM PincodeLookup p
            WHERE p.isActive    = true
              AND p.latitude    IS NOT NULL
              AND p.longitude   IS NOT NULL
              AND p.district    = :district
            ORDER BY p.areaName ASC
            """)
    List<PincodeLookup> findByDistrictWithCoordinates(@Param("district") String district);

    /**
     * Find the closest active pincode using the Haversine formula.
     */
    @Query(value = """
            SELECT * FROM pincode_lookup
            WHERE is_active = true
              AND latitude IS NOT NULL
              AND longitude IS NOT NULL
            ORDER BY (
              6371 * acos(
                  cos(radians(:lat)) * cos(radians(latitude)) * 
                  cos(radians(longitude) - radians(:lng)) + 
                  sin(radians(:lat)) * sin(radians(latitude))
              )
            ) ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<PincodeLookup> findClosestPincode(@Param("lat") double lat, @Param("lng") double lng);

    // =========================================================================
    // DISTINCT VALUES — DROPDOWNS & ANALYTICS
    // =========================================================================

    /**
     * Get all distinct active states, ordered alphabetically.
     */
    @Query("SELECT DISTINCT p.state FROM PincodeLookup p WHERE p.isActive = true ORDER BY p.state ASC")
    List<String> findDistinctActiveStates();

    /**
     * Backward-compatibility alias for findDistinctActiveStates().
     * Keeps existing service call sites working without modification.
     */
    @Query("SELECT DISTINCT p.state FROM PincodeLookup p WHERE p.isActive = true ORDER BY p.state ASC")
    List<String> findDistinctStatesByIsActiveTrue();

    @Query("SELECT DISTINCT p.district FROM PincodeLookup p WHERE p.isActive = true ORDER BY p.district ASC")
    List<String> findDistinctDistrictsByIsActiveTrue();

    @Query("""
            SELECT DISTINCT p.district FROM PincodeLookup p
            WHERE p.isActive = true AND p.state = :state
            ORDER BY p.district ASC
            """)
    List<String> findDistinctActiveDistrictsByState(@Param("state") String state);

    @Query("""
            SELECT DISTINCT p.district FROM PincodeLookup p
            WHERE p.isActive = true AND LOWER(p.state) = LOWER(:state)
            ORDER BY p.district ASC
            """)
    List<String> findDistinctDistrictsByStateIgnoreCaseAndIsActiveTrue(@Param("state") String state);

    @Query("""
            SELECT DISTINCT p.city FROM PincodeLookup p
            WHERE p.isActive = true AND p.district = :district
            ORDER BY p.city ASC
            """)
    List<String> findDistinctActiveCitiesByDistrict(@Param("district") String district);

    // =========================================================================
    // PREFIX EXTRACTION — for state/district-scoped community matching
    // =========================================================================

    /**
     * Get distinct pincode prefixes of given length for a state.
     * e.g. first 2 digits → state-level scope bucket.
     */
    @Query("""
            SELECT DISTINCT SUBSTRING(p.pincode, 1, :prefixLength) FROM PincodeLookup p
            WHERE p.isActive = true AND LOWER(p.state) = LOWER(:state)
            """)
    List<String> findDistinctPrefixesByStateIgnoreCase(@Param("state") String state,
                                                       @Param("prefixLength") int prefixLength);

    /**
     * Get distinct pincode prefixes of given length for a district.
     * e.g. first 4 digits → district-level scope bucket.
     */
    @Query("""
            SELECT DISTINCT SUBSTRING(p.pincode, 1, :prefixLength) FROM PincodeLookup p
            WHERE p.isActive = true AND LOWER(p.district) = LOWER(:district)
            """)
    List<String> findDistinctPrefixesByDistrictIgnoreCase(@Param("district") String district,
                                                          @Param("prefixLength") int prefixLength);

    // =========================================================================
    // MEMORY-SAFE AGGREGATION & PAGINATION
    // =========================================================================

    @Query("SELECT COUNT(p) FROM PincodeLookup p WHERE p.isActive = true")
    long countActivePincodes();

    @Query("SELECT COUNT(p) FROM PincodeLookup p WHERE p.isActive = false")
    long countInactivePincodes();

    @Query("SELECT p.state, COUNT(p) FROM PincodeLookup p WHERE p.isActive = true GROUP BY p.state")
    List<Object[]> countActivePincodesByState();

    @Query("SELECT p.state, p.district, COUNT(p) FROM PincodeLookup p WHERE p.isActive = true GROUP BY p.state, p.district")
    List<Object[]> countActivePincodesByDistrict();

    @Query("SELECT p FROM PincodeLookup p WHERE (:beforePincode IS NULL OR p.pincode < :beforePincode) ORDER BY p.pincode DESC")
    List<PincodeLookup> findCursorPage(@Param("beforePincode") String beforePincode, org.springframework.data.domain.Pageable pageable);
}
