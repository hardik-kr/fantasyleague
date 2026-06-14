package com.cricket.fantasyleague.security;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> allowedOrigins;

    public OriginValidationFilter(@Value("${security.allowed-origins:http://localhost:3000}") String allowedOriginsCsv) {
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
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
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String sourceOrigin = origin != null && !origin.isBlank() ? origin : originFromReferer(referer);

        if (sourceOrigin == null || !allowedOrigins.contains(sourceOrigin)) {
            writeForbidden(response, "Invalid request origin");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) {
                return null;
            }
            return scheme + "://" + host + (port >= 0 ? ":" + port : "");
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        ApiResponse resp = new ApiResponse(message, false, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(resp));
    }
}
