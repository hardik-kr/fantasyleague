package com.cricket.fantasyleague.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

    private final AuthCookieService service = new AuthCookieService(
            "fl_access_token",
            "fl_refresh_token",
            "fl_csrf",
            900,
            2592000,
            false,
            "Lax");

    @Test
    void setAuthCookiesUsesExpectedPathsAndHttpOnlyFlags() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setAccessCookie(response, "access.jwt");
        service.setRefreshCookie(response, "refresh-token");
        service.setCsrfCookie(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(3);
        assertThat(cookies.get(0))
                .contains("fl_access_token=access.jwt")
                .contains("Path=/api")
                .contains("Max-Age=900")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(cookies.get(1))
                .contains("fl_refresh_token=refresh-token")
                .contains("Path=/auth")
                .contains("Max-Age=2592000")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(cookies.get(2))
                .contains("fl_csrf=")
                .contains("Path=/")
                .contains("Max-Age=2592000")
                .contains("SameSite=Lax")
                .doesNotContain("HttpOnly");
    }

    @Test
    void clearAuthCookiesExpiresAllCookiePaths() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearAuthCookies(response);

        assertThat(response.getHeaders("Set-Cookie"))
                .hasSize(3)
                .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }
}
