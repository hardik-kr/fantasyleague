package com.cricket.fantasyleague.service.otp;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.payload.dto.UserDto;

@Service
public class OtpService {

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;
    private static final int CLEANUP_THRESHOLD = 50;

    @Value("${otp.validity.minutes:5}")
    private int validityMinutes;

    @Value("${otp.max.attempts:3}")
    private int maxAttempts;

    @Value("${otp.rate-limit.window.minutes:10}")
    private int rateLimitWindowMinutes;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitStore = new ConcurrentHashMap<>();

    public record OtpEntry(String otp, UserDto userData, Instant expiresAt) {}
    private record RateLimitEntry(int count, Instant windowStart) {}

    public String generateAndStore(String email, UserDto userData) {
        lazyCleanup();
        checkRateLimit(email);

        String otp = generateOtp();
        Instant expiresAt = Instant.now().plusSeconds(validityMinutes * 60L);
        otpStore.put(email.toLowerCase(), new OtpEntry(otp, userData, expiresAt));

        String key = email.toLowerCase();
        rateLimitStore.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart().plusSeconds(rateLimitWindowMinutes * 60L).isBefore(Instant.now())) {
                return new RateLimitEntry(1, Instant.now());
            }
            return new RateLimitEntry(existing.count() + 1, existing.windowStart());
        });

        logger.info("OTP generated for email={}", email);
        return otp;
    }

    public OtpEntry validate(String email, String otp) {
        OtpEntry entry = otpStore.get(email.toLowerCase());
        if (entry == null) {
            throw new IllegalArgumentException("No OTP found for this email. Please request a new one.");
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            otpStore.remove(email.toLowerCase());
            throw new IllegalArgumentException("OTP has expired. Please request a new one.");
        }
        if (!entry.otp().equals(otp)) {
            throw new IllegalArgumentException("Invalid OTP. Please try again.");
        }
        otpStore.remove(email.toLowerCase());
        logger.info("OTP verified successfully for email={}", email);
        return entry;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private void checkRateLimit(String email) {
        RateLimitEntry entry = rateLimitStore.get(email.toLowerCase());
        if (entry != null) {
            boolean windowActive = entry.windowStart()
                    .plusSeconds(rateLimitWindowMinutes * 60L)
                    .isAfter(Instant.now());
            if (windowActive && entry.count() >= maxAttempts) {
                throw new IllegalStateException(
                        "Too many OTP requests. Please try again after " + rateLimitWindowMinutes + " minutes.");
            }
        }
    }

    private void lazyCleanup() {
        if (otpStore.size() + rateLimitStore.size() < CLEANUP_THRESHOLD) {
            return;
        }
        Instant now = Instant.now();
        otpStore.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
        rateLimitStore.entrySet().removeIf(e ->
                now.isAfter(e.getValue().windowStart().plusSeconds(rateLimitWindowMinutes * 60L)));
    }
}
