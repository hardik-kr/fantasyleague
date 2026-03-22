package com.cricket.fantasyleague.security;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Adds {@code Authorization: Bearer &lt;token&gt;} for calls to cricketapi.
 * Token must match {@code cricketapi.universal-auth.token} configured on cricketapi.
 */
public class CricketServiceBearerRequestInterceptor implements ClientHttpRequestInterceptor {

    private final String baseUrlPrefix;
    private final String bearerToken;

    public CricketServiceBearerRequestInterceptor(String cricketApiBaseUrl, String bearerToken) {
        String trimmed = cricketApiBaseUrl.trim();
        this.baseUrlPrefix = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        this.bearerToken = bearerToken;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String uri = request.getURI().toString();
        if (uri.startsWith(baseUrlPrefix)) {
            request.getHeaders().setBearerAuth(bearerToken);
        }
        return execution.execute(request, body);
    }
}
