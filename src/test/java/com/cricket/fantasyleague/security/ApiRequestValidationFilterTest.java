package com.cricket.fantasyleague.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRequestValidationFilterTest {

    @Test
    void securedApiRequestPassesWithBrowserXhrHeaders() throws Exception {
        ApiRequestValidationFilter filter = new ApiRequestValidationFilter();
        MockHttpServletRequest request = apiRequest("GET", "/api/me");
        addBrowserXhrHeaders(request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void directBrowserNavigationToSecuredApiFailsEvenWithXmlHttpRequestHeader() throws Exception {
        ApiRequestValidationFilter filter = new ApiRequestValidationFilter();
        MockHttpServletRequest request = apiRequest("GET", "/api/me");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.addHeader("Sec-Fetch-Dest", "document");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Invalid API request source");
    }

    @Test
    void securedApiRequestWithoutXmlHttpRequestHeaderFails() throws Exception {
        ApiRequestValidationFilter filter = new ApiRequestValidationFilter();
        MockHttpServletRequest request = apiRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void xmlHttpRequestHeaderWithoutBrowserFetchMetadataFails() throws Exception {
        ApiRequestValidationFilter filter = new ApiRequestValidationFilter();
        MockHttpServletRequest request = apiRequest("GET", "/api/me");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void externalClientKeyDoesNotBypassBrowserRequestValidation() throws Exception {
        ApiRequestValidationFilter filter = new ApiRequestValidationFilter();
        MockHttpServletRequest request = apiRequest("GET", "/api/me");
        request.addHeader("X-External-Client-Key", "dev-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest apiRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private void addBrowserXhrHeaders(MockHttpServletRequest request) {
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.addHeader("Sec-Fetch-Mode", "cors");
        request.addHeader("Sec-Fetch-Dest", "empty");
        request.addHeader("Sec-Fetch-Site", "same-site");
    }
}
