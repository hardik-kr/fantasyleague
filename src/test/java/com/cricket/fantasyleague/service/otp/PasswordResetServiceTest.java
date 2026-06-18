package com.cricket.fantasyleague.service.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.user.UserService;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserService userService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, emailService, userService);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "otpValidityMinutes", 5);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "rateLimitWindowMinutes", 10);
        ReflectionTestUtils.setField(service, "resetTokenValidityMinutes", 5);
    }

    @Test
    void initiateReturnsWithoutEmailForUnknownAccount() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(null);

        service.initiate("missing@example.com");

        verify(emailService, never()).sendPasswordResetOtpEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void initiateSendsOtpForRegisteredEmail() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));

        service.initiate("user@example.com");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtpEmail(org.mockito.ArgumentMatchers.eq("user@example.com"), otpCaptor.capture());
        assertThat(otpCaptor.getValue()).hasSize(6).containsOnlyDigits();
    }

    @Test
    void verifyRejectsInvalidOtp() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));
        service.initiate("user@example.com");

        assertThatThrownBy(() -> service.verify("user@example.com", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid OTP");
    }

    @Test
    void verifyRejectsExpiredOtp() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));
        service.initiate("user@example.com");
        String otp = sentOtp();

        service.setClock(Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.verify("user@example.com", otp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyReturnsResetTokenForValidOtp() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));
        service.initiate("user@example.com");

        String token = service.verify("user@example.com", sentOtp());

        assertThat(token).isNotBlank();
    }

    @Test
    void completeRejectsMismatchedPasswords() {
        assertThatThrownBy(() -> service.complete("user@example.com", "token", "newpass1", "different"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
    }

    @Test
    void completeRejectsInvalidToken() {
        assertThatThrownBy(() -> service.complete("user@example.com", "bad-token", "newpass1", "newpass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid password reset token");
    }

    @Test
    void completeRejectsExpiredToken() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));
        service.initiate("user@example.com");
        String token = service.verify("user@example.com", sentOtp());

        service.setClock(Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.complete("user@example.com", token, "newpass1", "newpass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void completeRejectsUnknownEmailAfterVerification() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));
        service.initiate("user@example.com");
        String token = service.verify("user@example.com", sentOtp());
        when(userRepository.findByEmail("user@example.com")).thenReturn(null);

        assertThatThrownBy(() -> service.complete("user@example.com", token, "newpass1", "newpass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid password reset request");
    }

    @Test
    void completeUpdatesPasswordAndConsumesToken() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(user("user@example.com"));
        service.initiate("user@example.com");
        String token = service.verify("user@example.com", sentOtp());

        service.complete("user@example.com", token, "newpass1", "newpass1");

        verify(userService).updatePassword("user@example.com", "newpass1");
        assertThatThrownBy(() -> service.complete("user@example.com", token, "newpass2", "newpass2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid password reset token");
    }

    private String sentOtp() {
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtpEmail(org.mockito.ArgumentMatchers.eq("user@example.com"), otpCaptor.capture());
        return otpCaptor.getValue();
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        return user;
    }
}
