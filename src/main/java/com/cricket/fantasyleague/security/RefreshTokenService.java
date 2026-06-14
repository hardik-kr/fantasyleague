package com.cricket.fantasyleague.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final StringRedisTemplate redisTemplate;
    private final AuthCookieService authCookieService;
    private final String secret;
    private final long refreshTokenValiditySeconds;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            AuthCookieService authCookieService,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.refresh-token-validity-seconds:2592000}") long refreshTokenValiditySeconds) {
        this.redisTemplate = redisTemplate;
        this.authCookieService = authCookieService;
        this.secret = secret;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String create(String username) {
        String tokenId = authCookieService.randomTokenPart(18);
        String tokenSecret = authCookieService.randomTokenPart(32);
        String token = tokenId + "." + tokenSecret;

        String key = redisKey(tokenId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "username", username,
                "secretHash", hash(tokenSecret),
                "expiresAt", Instant.now().plusSeconds(refreshTokenValiditySeconds).toString()
        ));
        redisTemplate.expire(key, Duration.ofSeconds(refreshTokenValiditySeconds));

        return token;
    }

    public RefreshTokenRotation rotate(String refreshToken) {
        RefreshTokenParts parts = parse(refreshToken);
        String key = redisKey(parts.tokenId());
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(key);
        if (stored.isEmpty()) {
            throw unauthorized("Invalid refresh token");
        }

        String expectedHash = String.valueOf(stored.get("secretHash"));
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                hash(parts.tokenSecret()).getBytes(StandardCharsets.UTF_8))) {
            redisTemplate.delete(key);
            throw unauthorized("Invalid refresh token");
        }

        String username = String.valueOf(stored.get("username"));
        redisTemplate.delete(key);
        return new RefreshTokenRotation(username, create(username));
    }

    public void revoke(String refreshToken) {
        parseOptional(refreshToken)
                .map(parts -> redisKey(parts.tokenId()))
                .ifPresent(redisTemplate::delete);
    }

    private RefreshTokenParts parse(String refreshToken) {
        return parseOptional(refreshToken)
                .orElseThrow(() -> unauthorized("Invalid refresh token"));
    }

    private Optional<RefreshTokenParts> parseOptional(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        String[] parts = refreshToken.split("\\.", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RefreshTokenParts(parts[0], parts[1]));
    }

    private String redisKey(String tokenId) {
        return KEY_PREFIX + tokenId;
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private record RefreshTokenParts(String tokenId, String tokenSecret) {
    }

    public record RefreshTokenRotation(String username, String refreshToken) {
    }
}
