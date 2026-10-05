package com.govlyx.AI.repository;

import com.govlyx.AI.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    // =========================================================================
    // BASIC USER QUERIES
    // =========================================================================

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailHash(String emailHash);
    Optional<User> findByGoogleId(String googleId);
    List<User> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByEmailHash(String emailHash);

    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByEmailUpdateToken(String token);

    Optional<User> findByUsernameAndIsActiveTrue(String username);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.emailHash = :emailHash")
    Optional<User> findByEmailHashWithRole(@Param("emailHash") String emailHash);

    /**
     * JOIN FETCH version of findByEmail for the Spring Security authentication path.
     * User.role is FetchType.LAZY — this loads both in ONE query, eliminating the
     * extra round-trip on every authenticated API request.
     *
     * FIX: This method must be used in CustomUserDetailsService.loadUserByUsername()
     * instead of plain findByEmail() to avoid the "could not initialize proxy - no Session"
     * error caused by accessing the lazy Role outside a Hibernate session.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithRole(@Param("email") String email);

    /**
     * JOIN FETCH version of findByUsername for cases where the role is
     * needed immediately (e.g., admin permission checks in the same request).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :username")
    Optional<User> findByUsernameWithRole(@Param("username") String username);

    /**
     * JOIN FETCH version of findById — required by CustomUserDetailsService.loadUserById().
     *
     * The JWT filter calls loadUserById() on every authenticated request, then
     * immediately calls getAuthorities() → role.getName(). Without JOIN FETCH,
     * Role is a lazy proxy and the Hibernate session is already closed by the
     * time getAuthorities() is invoked → LazyInitializationException → 403 on
     * every protected endpoint.
     *
     * This query loads User + Role in ONE SQL query, guaranteeing the Role is
     * always initialized regardless of transaction boundaries.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") Long id);

    /**
     * FIX: CommunityInviteService calls findByActualUsername().
     * In User.java, getActualUsername() returns the `username` column — there is
     * no separate DB column. This query searches by u.username under the name
     * the service expects, so the service needs no changes.
     *
     * FIX (PostgreSQL): Changed "u.isActive = true" — explicit boolean comparison
     * is required on PostgreSQL to avoid "could not determine data type of parameter"
     * errors when Hibernate maps the Boolean field.
     */
    @Query("SELECT u FROM User u WHERE (LOWER(u.username) = LOWER(:username) OR LOWER(u.email) = LOWER(:username)) AND u.isActive = true")
    List<User> findByActualUsername(@Param("username") String username);

    // =========================================================================
    // PINCODE — EXACT & PREFIX
    // =========================================================================

    List<User> findByPincodeAndIsActiveTrue(String pincode, Pageable pageable);

    List<User> findByPincodeStartingWithAndIsActiveTrue(String pincodePrefix, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.hasInvalidPincode IS NULL AND u.isActive = true")
    List<User> findUsersWithUnverifiedPincode(Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.pincode = :pincode AND u.isActive = true")
    long countActiveUsersByPincode(@Param("pincode") String pincode);

    @Query("SELECT u FROM User u WHERE u.pincode = :pincode AND u.isActive = true ORDER BY u.id ASC")
    List<User> findActiveUsersByPincode(@Param("pincode") String pincode, Pageable pageable);

    /**
     * FIX (PostgreSQL): SUBSTRING(col, start, length) syntax is standard SQL.
     * PostgreSQL also supports SUBSTRING(col FROM start FOR length) but the
     * positional version used here works on both MySQL and PostgreSQL via JPQL.
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 2) = :statePrefix")
    List<User> findByStatePrefixAndIsActiveTrue(@Param("statePrefix") String statePrefix, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "AND SUBSTRING(u.pincode, 1, 3) = :districtPrefix")
    List<User> findByDistrictPrefixAndIsActiveTrue(@Param("districtPrefix") String districtPrefix, Pageable pageable);

    // =========================================================================
    // ROLE-BASED QUERIES
    // =========================================================================

    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    List<User> findByRoleNameAndIsActiveTrue(@Param("roleName") String roleName);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode = :pincode AND u.isActive = true " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeAndIsActiveTrueOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("pincode") String pincode,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode = :pincode AND u.isActive = true " +
            "AND u.id < :beforeId ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("pincode") String pincode,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode LIKE CONCAT(:prefix, '%') AND u.isActive = true " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("prefix") String prefix,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.pincode LIKE CONCAT(:prefix, '%') AND u.isActive = true " +
            "AND u.id < :beforeId ORDER BY u.id DESC")
    List<User> findByRoleNameAndPincodeStartingWithAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("prefix") String prefix,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // =========================================================================
    // ACTIVE USER LISTING — cursor-paginated
    // =========================================================================

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.id < :beforeId ORDER BY u.id DESC")
    List<User> findByIsActiveTrueAndIdLessThanOrderByIdDesc(
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.id DESC")
    List<User> findByIsActiveTrueOrderByIdDesc(Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.isActive = true AND u.id < :beforeId " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameAndIdLessThanOrderByIdDesc(
            @Param("roleName") String roleName,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.isActive = true " +
            "ORDER BY u.id DESC")
    List<User> findByRoleNameOrderByIdDesc(
            @Param("roleName") String roleName,
            Pageable pageable);

    // =========================================================================
    // RECENTLY CREATED USERS — cursor-paginated
    // =========================================================================

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :createdAt " +
            "AND u.id < :cursor " +
            "ORDER BY u.createdAt DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterAndIdLessThanOrderByCreatedAtDesc(
            @Param("createdAt") Timestamp createdAt,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive = true " +
            "AND u.createdAt > :createdAt " +
            "ORDER BY u.createdAt DESC")
    List<User> findByIsActiveTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("createdAt") Timestamp createdAt,
            Pageable pageable);

    // =========================================================================
    // USER SEARCH / TAGGING
    // =========================================================================

    @Query("SELECT u FROM User u JOIN FETCH u.role r WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\' " +
            "ORDER BY u.username ASC")
    List<User> searchUsersForTagging(
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT u FROM User u JOIN FETCH u.role r WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\' " +
            "AND u.id < :beforeId " +
            "ORDER BY u.username ASC")
    List<User> searchUsersForTaggingWithCursor(
            @Param("query") String query,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // =========================================================================
    // BATCH / IN-CLAUSE LOOKUPS
    // =========================================================================

    @Query("SELECT u FROM User u WHERE u.username IN :usernames AND u.isActive = true")
    List<User> findByUsernameInAndIsActiveTrue(@Param("usernames") List<String> usernames);

    List<User> findByUsernameIn(List<String> usernames);

    @Query("SELECT u.id, r.name FROM User u JOIN u.role r WHERE u.id IN :userIds")
    List<Object[]> findUserRolesByUserIds(@Param("userIds") List<Long> userIds);

    // =========================================================================
    // SEARCH / SUGGESTION
    // =========================================================================

    @Query("SELECT u FROM User u JOIN FETCH u.role r WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\' " +
            "ORDER BY u.username ASC")
    List<User> searchByUsername(@Param("query") String query, Pageable pageable);

    @Query("SELECT u FROM User u JOIN FETCH u.role r WHERE u.isActive = true " +
            "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\' " +
            "AND u.id < :beforeId ORDER BY u.username ASC")
    List<User> searchByUsernameWithCursor(
            @Param("query") String query,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    // =========================================================================
    // ROLE + SEARCH WITH CURSOR
    // =========================================================================

    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :role " +
            "AND u.isActive = true " +
            "AND u.id < :cursor " +
            "AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\' " +
            "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\') " +
            "ORDER BY u.id DESC")
    List<User> searchUsersByRoleAndQueryWithCursor(
            @Param("role") String role,
            @Param("query") String query,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // =========================================================================
    // GEOGRAPHIC DISTRIBUTION
    // =========================================================================

    /**
     * FIX (PostgreSQL): LENGTH() is standard SQL and works on PostgreSQL.
     * CONCAT and GROUP BY on a scalar column are also fully supported.
     * No changes needed from MySQL version for this query.
     */
    @Query("SELECT u.pincode, COUNT(u) FROM User u " +
            "WHERE u.isActive = true " +
            "AND u.pincode IS NOT NULL " +
            "AND LENGTH(u.pincode) = 6 " +
            "GROUP BY u.pincode " +
            "ORDER BY COUNT(u) DESC")
    List<Object[]> getUserDistributionByPincode();

    // =========================================================================
    // ADMIN / STATS
    // =========================================================================

    /**
     * HyperlocalSeedService calls findFirstByRoleName(Constant.ROLE_ADMIN)
     * to find a system owner for seeded communities.
     * Returns the first active user with the given role ordered by id ASC so
     * the result is deterministic (lowest-id admin = earliest-created = stable).
     */
    @Query("SELECT u FROM User u JOIN u.role r " +
            "WHERE r.name = :roleName AND u.isActive = true " +
            "ORDER BY u.id ASC")
    Optional<User> findFirstByRoleName(@Param("roleName") String roleName);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();

    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    long countActiveByRoleName(@Param("roleName") String roleName);

    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true " +
           "AND (u.updatedAt >= :since OR u.createdAt >= :since)")
    long countActiveByRoleNameAndActiveSince(@Param("roleName") String roleName, @Param("since") Date since);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since")
    long countUsersCreatedAfter(@Param("since") Timestamp since);

    /**
     * FIX (PostgreSQL): u.isActive = false — explicit boolean comparison
     * prevents Hibernate from generating an ambiguous type cast on PostgreSQL.
     */
    @Query("SELECT u FROM User u WHERE u.isActive = false ORDER BY u.updatedAt DESC")
    List<User> findInactiveUsers(Pageable pageable);
}
