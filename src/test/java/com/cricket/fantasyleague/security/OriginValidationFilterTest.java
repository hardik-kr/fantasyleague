package com.cricket.fantasyleague.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginValidationFilterTest {

    @Test
    void unsafeSecuredRequestPassesForAllowedOrigin() throws Exception {
        OriginValidationFilter filter = new OriginValidationFilter("http://localhost:3000");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/daily/team");
        request.addHeader("Origin", "http://localhost:3000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unsafeSecuredRequestFailsForMissingOriginAndReferer() throws Exception {
        OriginValidationFilter filter = new OriginValidationFilter("http://localhost:3000");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/daily/team");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Invalid request origin");
    }

    @Test
    void unsafeSecuredRequestCanUseAllowedRefererOrigin() throws Exception {
        OriginValidationFilter filter = new OriginValidationFilter("http://localhost:3000");
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/daily/team/1");
        request.addHeader("Referer", "http://localhost:3000/season/create-team");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
