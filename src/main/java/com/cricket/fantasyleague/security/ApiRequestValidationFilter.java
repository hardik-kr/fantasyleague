package com.cricket.fantasyleague.security;

import java.io.IOException;
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
public class ApiRequestValidationFilter extends OncePerRequestFilter {

    private static final String XHR_HEADER = "X-Requested-With";
    private static final String XHR_VALUE = "XMLHttpRequest";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isFrontendBrowserRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeForbidden(response, "Invalid API request source");
    }

    private boolean isFrontendBrowserRequest(HttpServletRequest request) {
        if (!XHR_VALUE.equals(request.getHeader(XHR_HEADER))) {
            return false;
        }

        String fetchMode = request.getHeader("Sec-Fetch-Mode");
        String fetchDest = request.getHeader("Sec-Fetch-Dest");
        String fetchSite = request.getHeader("Sec-Fetch-Site");

        return "cors".equalsIgnoreCase(fetchMode)
                && "empty".equalsIgnoreCase(fetchDest)
                && ("same-origin".equalsIgnoreCase(fetchSite) || "same-site".equalsIgnoreCase(fetchSite));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        ApiResponse resp = new ApiResponse(message, false, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(resp));
    }
}
