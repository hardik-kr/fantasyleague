package com.cricket.fantasyleague.service.otp;

import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${mail.dry-run:false}")
    private boolean dryRun;

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String otp) {
        if (dryRun) {
            logger.info("[DRY-RUN] OTP for {} is {}", toEmail, otp);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(new InternetAddress(fromEmail, "Fantasy League"));
            helper.setTo(toEmail);
            helper.setSubject("Fantasy League - Email Verification OTP");
            helper.setText(buildOtpEmailBody(otp), true);

            mailSender.send(message);
            logger.info("OTP email sent to {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email. Please try again.");
        }
    }

    private String buildOtpEmailBody(String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; \
                background: #1a1a2e; color: #ffffff; border-radius: 16px;">
                  <h1 style="text-align: center; color: #00e68a; font-size: 28px; letter-spacing: 2px;">
                    FANTASY LEAGUE
                  </h1>
                  <p style="text-align: center; color: #aaa; font-size: 14px;">Email Verification</p>
                  <div style="text-align: center; margin: 32px 0; padding: 24px; background: #16213e; \
                border-radius: 12px; border: 1px solid #333;">
                    <p style="color: #aaa; font-size: 12px; margin: 0 0 8px 0;">Your verification code is</p>
                    <h2 style="color: #00e68a; font-size: 36px; letter-spacing: 8px; margin: 0;">%s</h2>
                  </div>
                  <p style="text-align: center; color: #888; font-size: 12px;">
                    This code expires in 5 minutes.<br/>If you did not request this, please ignore this email.
                  </p>
                </div>
                """.formatted(otp);
    }
}
