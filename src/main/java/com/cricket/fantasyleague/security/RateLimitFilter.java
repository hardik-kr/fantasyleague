package com.cricket.fantasyleague.security;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int CLEANUP_THRESHOLD = 100;

    @Value("${ratelimit.global.requests-per-minute:30}")
    private int globalLimit;

    @Value("${ratelimit.auth.requests-per-minute:5}")
    private int authLimit;

    @Value("${ratelimit.write.requests-per-minute:10}")
    private int writeLimit;

    private final ConcurrentHashMap<String, RequestWindow> windowStore = new ConcurrentHashMap<>();

    private record RequestWindow(AtomicInteger count, Instant windowStart) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        lazyCleanup();

        String clientIp = resolveClientIp(request);
        String path = request.getServletPath();
        String method = request.getMethod();

        // Layer 1: per-IP global limit (all requests)
        if (isLimited(clientIp + ":global", globalLimit)) {
            reject(response, clientIp, path);
            return;
        }

        // Layer 2: stricter limit for auth endpoints
        if (path.startsWith("/auth/") && isLimited(clientIp + ":auth", authLimit)) {
            reject(response, clientIp, path);
            return;
        }

        // Layer 3: per-IP limit on write operations (POST/PUT/DELETE on non-auth endpoints)
        boolean isWriteOp = !path.startsWith("/auth/") && ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method));
        if (isWriteOp && isLimited(clientIp + ":write", writeLimit)) {
            reject(response, clientIp, path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLimited(String key, int limit) {
        RequestWindow window = windowStore.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || now.isAfter(existing.windowStart().plusSeconds(60))) {
                return new RequestWindow(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get() > limit;
    }

    private void reject(HttpServletResponse response, String ip, String path) throws IOException {
        logger.warn("Rate limit exceeded for ip={} path={}", ip, path);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"message\":\"Too many requests. Please slow down.\",\"success\":false,\"code\":429}");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void lazyCleanup() {
        if (windowStore.size() < CLEANUP_THRESHOLD) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(120);
        windowStore.entrySet().removeIf(e -> e.getValue().windowStart().isBefore(cutoff));
    }
}
