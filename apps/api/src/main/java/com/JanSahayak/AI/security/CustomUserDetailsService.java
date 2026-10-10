package com.JanSahayak.AI.security;

import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepo;

    /**
     * FIX 1: Added @Transactional so the Hibernate session stays open long enough
     * for Spring Security to call getAuthorities() on the returned User object.
     *
     * Without @Transactional, the session closed immediately after findByEmail()
     * returned. When Spring Security then called user.getAuthorities() → role.getName(),
     * Hibernate tried to initialize the lazy Role proxy — but there was no session.
     * Result: "could not initialize proxy - no Session" on every login attempt.
     *
     * FIX 2: Switched from findByEmail() to findByEmailWithRole() which uses
     * JOIN FETCH to load User + Role in a SINGLE SQL query. This is better than
     * relying on the transaction alone because:
     *   - No N+1: role is fetched in the same query, not in a second round-trip
     *   - No lazy-load surprise: role is always initialized when returned
     *   - Works correctly even if transaction boundaries shift in future refactors
     *
     * The findByEmailWithRole() query is already defined in UserRepo.java:
     *   @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepo.findByEmailWithRole(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + username));

        // User implements UserDetails — returned directly.
        // Role is already initialized via JOIN FETCH — getAuthorities() is safe.
        return user;
    }

    /**
     * FIX: Switched from findById() to findByIdWithRole() which uses JOIN FETCH.
     *
     * findById() does NOT fetch the lazy Role proxy — when the JWT filter later
     * calls user.getAuthorities() → role.getName(), Hibernate tries to initialize
     * the proxy but there is no session → LazyInitializationException → 403 on
     * every authenticated request.
     *
     * findByIdWithRole() loads User + Role in a single query, guaranteeing the
     * Role is always initialized regardless of transaction boundaries.
     */
    @Transactional
    @Cacheable(value = "authUserDetails", key = "#id")
    public UserDetails loadUserById(Long id) {
        User user = userRepo.findByIdWithRole(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with id: " + id));

        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new org.springframework.security.authentication.DisabledException("User account is disabled");
        }

        return user;
    }
}
