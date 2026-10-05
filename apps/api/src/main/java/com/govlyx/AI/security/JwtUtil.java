package com.Govlyx.AI.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import com.Govlyx.AI.model.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret:mySecretKeyForJWTTokenGenerationThatIsLongEnoughForHS256Algorithm}")
    private String jwtSecret;

    @Value("${jwt.expiration:900000}")
    private long jwtExpirationInMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        return generateToken(authentication, null);
    }

    public String generateToken(Authentication authentication, String serverActorToken) {
        User user = (User) authentication.getPrincipal();

        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationInMs);

        var builder = Jwts.builder()
                .setSubject(Long.toString(user.getId()))
                .claim("username", user.getActualUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole() != null ? user.getRole().getName() : null)
                .claim("isActive", user.getIsActive())
                .claim("sessionToken", user.getSessionToken())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate);

        if (serverActorToken != null) {
            builder.claim("serverActorToken", serverActorToken);
        }

        return builder.signWith(getSigningKey()).compact();
    }


    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, null);
    }

    public String generateToken(UserDetails userDetails, String serverActorToken) {
        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationInMs);

        String subject;
        String username = null;
        String email = null;
        String role = null;
        Boolean isActive = null;

        if (userDetails instanceof User) {
            User user = (User) userDetails;
            subject = Long.toString(user.getId());
            username = user.getActualUsername();
            email = user.getEmail();
            role = user.getRole() != null ? user.getRole().getName() : null;
            isActive = user.getIsActive();
        } else {
            subject = userDetails.getUsername();
            email = userDetails.getUsername(); // Fallback for non-User implementations
        }

        var builder = Jwts.builder()
                .setSubject(subject)
                .claim("username", username)
                .claim("email", email)
                .claim("role", role)
                .claim("isActive", isActive)
                .claim("sessionToken", userDetails instanceof User ? ((User) userDetails).getSessionToken() : null)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate);

        if (serverActorToken != null) {
            builder.claim("serverActorToken", serverActorToken);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("username", String.class);
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("role", String.class);
    }

    public Boolean getIsActiveFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("isActive", Boolean.class);
    }

    public String getSessionTokenFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("sessionToken", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getServerActorTokenFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("serverActorToken", String.class);
        } catch (Exception e) {
            return null;
        }
    }


    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException ex) {
            logger.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty.");
        }
        return false;
    }
    // Add these methods to your JwtUtil.java class

    /**
     * Extract expiration date from token
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getExpiration();
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            logger.error("Error checking token expiration", e);
            return true;
        }
    }

    /**
     * Get remaining time until token expires (in milliseconds)
     */
    public long getTokenRemainingTime(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Enhanced validation that explicitly checks expiration
     */
    public boolean validateTokenWithExpiration(String token) {
        if (!validateToken(token)) {
            return false;
        }

        if (isTokenExpired(token)) {
            logger.warn("Token validation failed: Token has expired");
            return false;
        }

        return true;
    }
}
