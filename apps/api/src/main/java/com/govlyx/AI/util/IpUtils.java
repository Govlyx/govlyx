package com.govlyx.AI.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class IpUtils {

    // A static salt. You can also override this via an environment variable for extra security.
    private static final String SALT = System.getenv("IP_HASH_SALT") != null 
            ? System.getenv("IP_HASH_SALT") 
            : "govlyx-anonymous-salt-2026";

    /**
     * Extracts the IP from the current Spring RequestContext, and returns a cryptographic hash.
     * Useful for capturing an anonymous identifier directly in Service layers.
     */
    public static String getClientIpFromContext() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return extractIp(request);
        }
        return null;
    }

    /**
     * Extracts the true client IP address from the request and hashes it.
     * We no longer return raw IP addresses to maintain true user anonymity.
     */
    public static String extractIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String rawIp = request.getRemoteAddr();

        String[] headersToCheck = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headersToCheck) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain a comma-separated list of IPs.
                if (ip.contains(",")) {
                    rawIp = ip.split(",")[0].trim();
                } else {
                    rawIp = ip;
                }
                break;
            }
        }

        return hashIp(rawIp);
    }

    /**
     * One-way cryptographic hash of the IP to ensure anonymity.
     */
    private static String hashIp(String rawIp) {
        if (rawIp == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = rawIp + SALT;
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (Exception e) {
            // Fallback in case SHA-256 is missing (extremely rare)
            return String.valueOf((rawIp + SALT).hashCode());
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
