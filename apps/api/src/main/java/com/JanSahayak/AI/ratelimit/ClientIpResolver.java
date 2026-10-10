package com.JanSahayak.AI.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ClientIpResolver {

    /**
     * Set to {@code false} if this application is exposed directly to the
     * internet without a trusted reverse-proxy layer sitting in front of it.
     *
     * When {@code false}, all proxy headers are ignored and
     * {@code request.getRemoteAddr()} is always used.  This prevents clients
     * from self-selecting their own rate-limit bucket via a forged XFF header.
     */
    private static final boolean TRUST_PROXY_HEADERS = true;

    /** Headers inspected in order of preference. */
    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    /**
     * Resolves the best-effort real client IP.
     *
     * @param request the incoming HTTP request
     * @return a non-null, non-blank IP string; falls back to {@code "unknown"}
     *         only if {@code getRemoteAddr()} itself returns null (never in practice)
     */
    public String resolve(HttpServletRequest request) {
        if (TRUST_PROXY_HEADERS) {
            for (String header : PROXY_HEADERS) {
                String value = request.getHeader(header);
                if (isValidHeaderValue(value)) {
                    String ip = extractBestIp(value);
                    if (ip != null) {
                        log.trace("[ClientIpResolver] Resolved via {}: {}", header, ip);
                        return sanitize(ip);
                    }
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank())
                ? sanitize(remoteAddr)
                : "unknown";
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Rejects null, blank, and the literal string "unknown" that some proxies inject. */
    private boolean isValidHeaderValue(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.strip());
    }

    /**
     * Parses a comma-separated XFF chain and returns the leftmost non-private IP.
     * Falls back to the leftmost IP overall if all entries are private (purely
     * internal deployment — still better than null).
     *
     * Returns {@code null} only if the chain is empty after splitting.
     */
    private String extractBestIp(String xffValue) {
        String[] parts = xffValue.split(",");
        String lastEntry = null;

        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = parts[i].strip();
            if (ip.isEmpty()) continue;

            if (lastEntry == null) lastEntry = ip;  // remember rightmost as fallback

            if (!isPrivateOrLoopback(ip)) {
                return ip;  // first public IP from the right — this is the real client
            }
        }

        // All IPs were private (pure internal network) — return rightmost
        return lastEntry;
    }

    /**
     * Lightweight RFC-1918 + loopback check.
     *
     * Covers:
     *   10.0.0.0/8        → 10.*
     *   172.16.0.0/12     → 172.16.* – 172.31.*
     *   192.168.0.0/16    → 192.168.*
     *   127.0.0.0/8       → 127.*   (IPv4 loopback)
     *   ::1               (IPv6 loopback)
     *
     * Deliberately avoids InetAddress.getByName() to prevent DNS lookup latency
     * on the hot path.
     */
    private boolean isPrivateOrLoopback(String ip) {
        if (ip == null || ip.isBlank()) return true;

        if (ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || "::1".equals(ip)) {
            return true;
        }

        // 172.16.0.0/12 → second octet 16–31
        if (ip.startsWith("172.")) {
            try {
                int dot2 = ip.indexOf('.', 4);
                if (dot2 > 4) {
                    int second = Integer.parseInt(ip.substring(4, dot2));
                    return second >= 16 && second <= 31;
                }
            } catch (NumberFormatException ignored) {
                // malformed — treat as non-private so it gets used
            }
        }

        return false;
    }

    /**
     * Strips IPv6 zone IDs and bracket notation for clean, consistent keys.
     *   "[::1]"        → "::1"
     *   "fe80::1%eth0" → "fe80::1"
     */
    private String sanitize(String ip) {
        return ip.replaceAll("[\\[\\]]", "")
                .replaceAll("%.*$", "")
                .strip();
    }
}
