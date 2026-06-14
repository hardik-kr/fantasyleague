package com.cricket.fantasyleague.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthCookieService {

    private final SecureRandom secureRandom = new SecureRandom();

    private final String accessCookieName;
    private final String refreshCookieName;
    private final String csrfCookieName;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;
    private final boolean secureCookie;
    private final String sameSite;

    public AuthCookieService(
            @Value("${jwt.access-cookie-name:fl_access_token}") String accessCookieName,
            @Value("${jwt.refresh-cookie-name:fl_refresh_token}") String refreshCookieName,
            @Value("${security.csrf-cookie-name:fl_csrf}") String csrfCookieName,
            @Value("${jwt.access-token-validity-seconds:900}") long accessTokenValiditySeconds,
            @Value("${jwt.refresh-token-validity-seconds:2592000}") long refreshTokenValiditySeconds,
            @Value("${security.cookie.secure:false}") boolean secureCookie,
            @Value("${security.cookie.same-site:Lax}") String sameSite) {
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.csrfCookieName = csrfCookieName;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, accessCookieName);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, refreshCookieName);
    }

    public Optional<String> readCsrfToken(HttpServletRequest request) {
        return readCookie(request, csrfCookieName);
    }

    public void setAccessCookie(HttpServletResponse response, String accessToken) {
        addCookie(response, ResponseCookie.from(accessCookieName, accessToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/api")
                .maxAge(accessTokenValiditySeconds)
                .build());
    }

    public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        addCookie(response, ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/auth")
                .maxAge(refreshTokenValiditySeconds)
                .build());
    }

    public String setCsrfCookie(HttpServletResponse response) {
        String csrfToken = randomTokenPart(32);
        addCookie(response, ResponseCookie.from(csrfCookieName, csrfToken)
                .httpOnly(false)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(refreshTokenValiditySeconds)
                .build());
        return csrfToken;
    }

    public void clearAuthCookies(HttpServletResponse response) {
        clearCookie(response, accessCookieName, "/api", true);
        clearCookie(response, refreshCookieName, "/auth", true);
        clearCookie(response, csrfCookieName, "/", false);
    }

    public String randomTokenPart(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void clearCookie(HttpServletResponse response, String name, String path, boolean httpOnly) {
        addCookie(response, ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(path)
                .maxAge(0)
                .build());
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
