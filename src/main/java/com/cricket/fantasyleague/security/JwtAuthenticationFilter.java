package com.cricket.fantasyleague.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cricket.fantasyleague.service.user.UserService;
import com.cricket.fantasyleague.util.AppConstants;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/login", "/auth/signup/", "/api/loadtest", "/api/masterdata", "/test/",
            "/actuator");

    /**
     * RFC-6750 prefix for bearer tokens — 7 chars including the trailing
     * space ("Bearer "). Held as a constant so the prefix length used for
     * the substring stays in lockstep with the literal we match against.
     * Previously the filter checked {@code startsWith("Bearer")} (no
     * space, 6 chars) and then unconditionally called {@code substring(7)}
     * — sending {@code Authorization: Bearer} (no token) would crash with
     * {@code StringIndexOutOfBoundsException: Range [7, 6) ...} because
     * the prefix-check would pass but there were no bytes left to slice.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtHelper jwtHelper;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtHelper jwtHelper, UserService userService) {
        this.jwtHelper = jwtHelper;
        this.userService = userService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestHeader = request.getHeader("Authorization");
        logger.debug("Header : {}", requestHeader);

        String username = null;
        String token = null;

        // Require the canonical "Bearer " prefix (with trailing space) AND
        // a non-empty token after it. Previously this checked
        // startsWith("Bearer") (no space) and then blindly substring(7),
        // which crashed for headers exactly equal to "Bearer" (length 6 →
        // substring(7) → StringIndexOutOfBoundsException). Empty / blank
        // tokens are now rejected up-front instead of being passed to
        // jwtHelper.getUsernameFromToken which would fail less clearly.
        String candidateToken = null;
        if (StringUtils.hasText(requestHeader)
                && requestHeader.startsWith(BEARER_PREFIX)
                && requestHeader.length() > BEARER_PREFIX.length()) {
            String slice = requestHeader.substring(BEARER_PREFIX.length()).trim();
            if (!slice.isEmpty()) {
                candidateToken = slice;
            }
        }

        if (candidateToken != null) {
            token = candidateToken;
            try {
                username = jwtHelper.getUsernameFromToken(token);
            } catch (IllegalArgumentException e) {
                logger.info(AppConstants.jwt.JWT_INVALID_USERNAME);
                request.setAttribute("error_msg", AppConstants.jwt.JWT_INVALID_USERNAME);
            } catch (ExpiredJwtException e) {
                logger.info(AppConstants.jwt.JWT_EXPIRED_TOKEN);
                request.setAttribute("error_msg", AppConstants.jwt.JWT_EXPIRED_TOKEN);
            } catch (MalformedJwtException e) {
                logger.info(AppConstants.jwt.JWT_INVALID_TOKEN);
                request.setAttribute("error_msg", AppConstants.jwt.JWT_INVALID_TOKEN);
            } catch (Exception e) {
                logger.info(AppConstants.jwt.JWT_SOMETHING_WENT_WRONG);
                request.setAttribute("error_msg", String.format(AppConstants.jwt.JWT_SOMETHING_WENT_WRONG, e.getMessage()));
            }
        } else {
            logger.debug(AppConstants.jwt.JWT_INVALID_HEADER);
            request.setAttribute("error_msg", AppConstants.jwt.JWT_INVALID_HEADER);
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userService.loadUserByUsername(username);
                if (jwtHelper.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.info(AppConstants.jwt.JWT_VERIFICATION_FAILS);
                    request.setAttribute("error_msg", AppConstants.jwt.JWT_VERIFICATION_FAILS);
                }
            } catch (BadCredentialsException be) {
                logger.info(AppConstants.user.USER_NOT_FOUND);
                request.setAttribute("error_msg", String.format(AppConstants.user.USER_NOT_FOUND, username));
            } catch (Exception e) {
                logger.info(AppConstants.jwt.JWT_SOMETHING_WENT_WRONG);
                request.setAttribute("error_msg", AppConstants.jwt.JWT_SOMETHING_WENT_WRONG);
            }
        }

        filterChain.doFilter(request, response);
    }
}
