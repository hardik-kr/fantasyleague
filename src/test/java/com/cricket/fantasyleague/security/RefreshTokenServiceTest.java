package com.cricket.fantasyleague.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    void rotateDeletesOldTokenAndCreatesReplacement() {
        AuthCookieService cookieService = new AuthCookieService("a", "r", "csrf", 900, 2592000, false, "Lax");
        RefreshTokenService service = new RefreshTokenService(redisTemplate, cookieService,
                "12345678901234567890123456789012", 2592000);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.expire(any(), eq(Duration.ofSeconds(2592000)))).thenReturn(true);

        String oldToken = service.create("user@example.com");
        String oldKey = redisKey(oldToken);
        Map<Object, Object> stored = captureStoredHash();
        when(hashOperations.entries(oldKey)).thenReturn(stored);

        RefreshTokenService.RefreshTokenRotation rotation = service.rotate(oldToken);

        assertThat(rotation.username()).isEqualTo("user@example.com");
        assertThat(rotation.refreshToken()).isNotEqualTo(oldToken);
        verify(redisTemplate).delete(oldKey);
    }

    @Test
    void reusedOrTamperedTokenDeletesSessionAndFails() {
        AuthCookieService cookieService = new AuthCookieService("a", "r", "csrf", 900, 2592000, false, "Lax");
        RefreshTokenService service = new RefreshTokenService(redisTemplate, cookieService,
                "12345678901234567890123456789012", 2592000);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.expire(any(), eq(Duration.ofSeconds(2592000)))).thenReturn(true);

        String oldToken = service.create("user@example.com");
        String oldKey = redisKey(oldToken);
        Map<Object, Object> stored = captureStoredHash();
        when(hashOperations.entries(oldKey)).thenReturn(stored);

        String tamperedToken = oldToken.replaceFirst("\\.[^.]+$", ".wrong-secret");
        assertThatThrownBy(() -> service.rotate(tamperedToken))
                .isInstanceOf(ResponseStatusException.class);
        verify(redisTemplate).delete(oldKey);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> captureStoredHash() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(any(String.class), captor.capture());
        return new HashMap<>(captor.getValue());
    }

    private String redisKey(String token) {
        return "auth:refresh:" + token.substring(0, token.indexOf('.'));
    }
}
