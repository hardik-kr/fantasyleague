package com.cricket.fantasyleague.service.otp;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int CLEANUP_THRESHOLD = 50;

    @Value("${login.lockout.max-attempts:3}")
    private int maxAttempts;

    @Value("${login.lockout.duration.minutes:15}")
    private int lockoutMinutes;

    private final ConcurrentHashMap<String, AttemptRecord> attemptStore = new ConcurrentHashMap<>();

    private record AttemptRecord(int failCount, Instant lockedUntil) {}

    public void checkLocked(String email) {
        lazyCleanup();
        AttemptRecord record = attemptStore.get(email.toLowerCase());
        if (record != null && record.lockedUntil() != null && Instant.now().isBefore(record.lockedUntil())) {
            long remainingSeconds = record.lockedUntil().getEpochSecond() - Instant.now().getEpochSecond();
            long remainingMinutes = (remainingSeconds / 60) + 1;
            throw new IllegalStateException(
                    "Account locked due to too many failed attempts. Try again in " + remainingMinutes + " minute(s).");
        }
    }

    public void recordFailure(String email) {
        String key = email.toLowerCase();
        attemptStore.compute(key, (k, existing) -> {
            if (existing == null || (existing.lockedUntil() != null && Instant.now().isAfter(existing.lockedUntil()))) {
                return new AttemptRecord(1, null);
            }
            int newCount = existing.failCount() + 1;
            if (newCount >= maxAttempts) {
                Instant lockUntil = Instant.now().plusSeconds(lockoutMinutes * 60L);
                logger.warn("Login locked for email={} until={}", email, lockUntil);
                return new AttemptRecord(newCount, lockUntil);
            }
            return new AttemptRecord(newCount, null);
        });
    }

    public void recordSuccess(String email) {
        attemptStore.remove(email.toLowerCase());
    }

    private void lazyCleanup() {
        if (attemptStore.size() < CLEANUP_THRESHOLD) {
            return;
        }
        Instant now = Instant.now();
        attemptStore.entrySet().removeIf(e ->
                e.getValue().lockedUntil() != null && now.isAfter(e.getValue().lockedUntil()));
    }
}
