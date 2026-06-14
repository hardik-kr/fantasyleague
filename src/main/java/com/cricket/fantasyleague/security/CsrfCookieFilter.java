package com.cricket.fantasyleague.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthCookieService authCookieService;
    private final String csrfHeaderName;

    public CsrfCookieFilter(
            AuthCookieService authCookieService,
            @Value("${security.csrf-header-name:X-CSRF-Token}") String csrfHeaderName) {
        this.authCookieService = authCookieService;
        this.csrfHeaderName = csrfHeaderName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !UNSAFE_METHODS.contains(request.getMethod())
                || "/".equals(path)
                || path.startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cookieToken = authCookieService.readCsrfToken(request).orElse("");
        String headerToken = request.getHeader(csrfHeaderName);

        if (headerToken == null || headerToken.isBlank() || !equalsConstantTime(cookieToken, headerToken.trim())) {
            writeForbidden(response, "Invalid CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean equalsConstantTime(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        ApiResponse resp = new ApiResponse(message, false, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(resp));
    }
}
