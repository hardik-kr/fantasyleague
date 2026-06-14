package com.cricket.fantasyleague.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfCookieFilterTest {

    @Test
    void unsafeSecuredRequestPassesWhenHeaderMatchesCookie() throws Exception {
        CsrfCookieFilter filter = new CsrfCookieFilter(cookieService("token-123"), "X-CSRF-Token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/daily/team");
        request.addHeader("X-CSRF-Token", "token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unsafeSecuredRequestFailsWhenHeaderIsMissing() throws Exception {
        CsrfCookieFilter filter = new CsrfCookieFilter(cookieService("token-123"), "X-CSRF-Token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/daily/team");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Invalid CSRF token");
    }

    private AuthCookieService cookieService(String token) {
        return new AuthCookieService("a", "r", "csrf", 900, 2592000, false, "Lax") {
            @Override
            public Optional<String> readCsrfToken(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.of(token);
            }
        };
    }
}
