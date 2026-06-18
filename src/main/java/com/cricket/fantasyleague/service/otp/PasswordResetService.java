package com.cricket.fantasyleague.service.otp;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.user.UserService;

@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int OTP_LENGTH = 6;
    private static final int CLEANUP_THRESHOLD = 50;

    @Value("${otp.validity.minutes:5}")
    private int otpValidityMinutes;

    @Value("${otp.max.attempts:3}")
    private int maxAttempts;

    @Value("${otp.rate-limit.window.minutes:10}")
    private int rateLimitWindowMinutes;

    @Value("${password-reset.token.validity.minutes:5}")
    private int resetTokenValidityMinutes;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResetTokenEntry> resetTokenStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitStore = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final UserService userService;
    private Clock clock = Clock.systemUTC();

    private record OtpEntry(String otp, Instant expiresAt) {}
    private record ResetTokenEntry(String token, Instant expiresAt) {}
    private record RateLimitEntry(int count, Instant windowStart) {}

    public PasswordResetService(UserRepository userRepository, EmailService emailService, UserService userService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.userService = userService;
    }

    public void initiate(String email) {
        lazyCleanup();
        String normalizedEmail = normalize(email);
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            logger.info("Password reset requested for unknown email={}", normalizedEmail);
            return;
        }

        checkRateLimit(normalizedEmail);
        String otp = generateOtp();
        otpStore.put(normalizedEmail, new OtpEntry(
                otp,
                now().plusSeconds(otpValidityMinutes * 60L)));
        recordRateLimitAttempt(normalizedEmail);
        emailService.sendPasswordResetOtpEmail(user.getEmail(), otp);
        logger.info("Password reset OTP initiated for email={}", normalizedEmail);
    }

    public String verify(String email, String otp) {
        lazyCleanup();
        String normalizedEmail = normalize(email);
        OtpEntry entry = otpStore.get(normalizedEmail);
        if (entry == null) {
            throw new IllegalArgumentException("No password reset OTP found for this email. Please request a new one.");
        }
        if (now().isAfter(entry.expiresAt())) {
            otpStore.remove(normalizedEmail);
            throw new IllegalArgumentException("Password reset OTP has expired. Please request a new one.");
        }
        if (!entry.otp().equals(otp)) {
            throw new IllegalArgumentException("Invalid OTP. Please try again.");
        }

        otpStore.remove(normalizedEmail);
        String resetToken = generateResetToken();
        resetTokenStore.put(normalizedEmail, new ResetTokenEntry(
                resetToken,
                now().plusSeconds(resetTokenValidityMinutes * 60L)));
        logger.info("Password reset OTP verified for email={}", normalizedEmail);
        return resetToken;
    }

    public void complete(String email, String resetToken, String password, String confirmPassword) {
        lazyCleanup();
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Password and confirm password must match.");
        }

        String normalizedEmail = normalize(email);
        ResetTokenEntry entry = resetTokenStore.get(normalizedEmail);
        if (entry == null) {
            throw new IllegalArgumentException("Invalid password reset token. Please verify OTP again.");
        }
        if (now().isAfter(entry.expiresAt())) {
            resetTokenStore.remove(normalizedEmail);
            throw new IllegalArgumentException("Password reset token has expired. Please verify OTP again.");
        }
        if (!entry.token().equals(resetToken)) {
            throw new IllegalArgumentException("Invalid password reset token. Please verify OTP again.");
        }
        if (userRepository.findByEmail(normalizedEmail) == null) {
            resetTokenStore.remove(normalizedEmail);
            throw new IllegalArgumentException("Invalid password reset request.");
        }

        userService.updatePassword(normalizedEmail, password);
        resetTokenStore.remove(normalizedEmail);
        logger.info("Password reset completed for email={}", normalizedEmail);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void checkRateLimit(String email) {
        RateLimitEntry entry = rateLimitStore.get(email);
        if (entry != null) {
            boolean windowActive = entry.windowStart()
                    .plusSeconds(rateLimitWindowMinutes * 60L)
                    .isAfter(now());
            if (windowActive && entry.count() >= maxAttempts) {
                throw new IllegalStateException(
                        "Too many password reset requests. Please try again after " + rateLimitWindowMinutes + " minutes.");
            }
        }
    }

    private void recordRateLimitAttempt(String email) {
        rateLimitStore.compute(email, (k, existing) -> {
            if (existing == null || existing.windowStart().plusSeconds(rateLimitWindowMinutes * 60L).isBefore(now())) {
                return new RateLimitEntry(1, now());
            }
            return new RateLimitEntry(existing.count() + 1, existing.windowStart());
        });
    }

    private void lazyCleanup() {
        if (otpStore.size() + resetTokenStore.size() + rateLimitStore.size() < CLEANUP_THRESHOLD) {
            return;
        }
        Instant current = now();
        otpStore.entrySet().removeIf(e -> current.isAfter(e.getValue().expiresAt()));
        resetTokenStore.entrySet().removeIf(e -> current.isAfter(e.getValue().expiresAt()));
        rateLimitStore.entrySet().removeIf(e ->
                current.isAfter(e.getValue().windowStart().plusSeconds(rateLimitWindowMinutes * 60L)));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
