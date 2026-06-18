package com.cricket.fantasyleague.service.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.dto.OtpVerifyRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetCompleteRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetInitiateRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetVerifyRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetVerifyResponse;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.jwtdto.JwtRequest;
import com.cricket.fantasyleague.payload.jwtdto.JwtResponse;
import com.cricket.fantasyleague.security.AuthCookieService;
import com.cricket.fantasyleague.security.JwtHelper;
import com.cricket.fantasyleague.security.RefreshTokenService;
import com.cricket.fantasyleague.service.otp.EmailService;
import com.cricket.fantasyleague.service.otp.LoginAttemptService;
import com.cricket.fantasyleague.service.otp.OtpService;
import com.cricket.fantasyleague.service.otp.PasswordResetService;
import com.cricket.fantasyleague.service.user.UserService;
import com.cricket.fantasyleague.util.AppConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(AuthWorkflowService.class);

    private final UserService userService;
    private final AuthenticationManager manager;
    private final JwtHelper helper;
    private final OtpService otpService;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final AuthCookieService authCookieService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    public AuthWorkflowService(UserService userService,
                               AuthenticationManager manager,
                               JwtHelper helper,
                               OtpService otpService,
                               EmailService emailService,
                               LoginAttemptService loginAttemptService,
                               AuthCookieService authCookieService,
                               RefreshTokenService refreshTokenService,
                               PasswordResetService passwordResetService) {
        this.userService = userService;
        this.manager = manager;
        this.helper = helper;
        this.otpService = otpService;
        this.emailService = emailService;
        this.loginAttemptService = loginAttemptService;
        this.authCookieService = authCookieService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
    }

    public ResponseEntity<JwtResponse> login(JwtRequest request, HttpServletResponse servletResponse) {
        loginAttemptService.checkLocked(request.getEmail());
        doAuthenticate(request.getEmail(), request.getPassword());
        loginAttemptService.recordSuccess(request.getEmail());

        UserDetails userDetails = userService.getUserByUserName(request.getEmail());
        return new ResponseEntity<>(issueAuthCookies(userDetails, servletResponse), HttpStatus.OK);
    }

    public ResponseEntity<ApiResponse> initiateSignup(UserDto request) {
        userService.validateNewUser(request);

        String otp = otpService.generateAndStore(request.getEmail(), request);
        emailService.sendOtpEmail(request.getEmail(), otp);

        logger.info("Signup OTP initiated for email={}", request.getEmail());
        ApiResponse response = new ApiResponse(
                "Verification code sent to " + request.getEmail(),
                true,
                HttpStatus.OK.value(),
                HttpStatus.OK
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<JwtResponse> verifySignup(OtpVerifyRequest request, HttpServletResponse servletResponse) {
        OtpService.OtpEntry entry = otpService.validate(request.getEmail(), request.getOtp());
        userService.createUser(entry.userData());

        logger.info("Signup completed for email={}", request.getEmail());
        UserDetails userDetails = userService.getUserByUserName(request.getEmail());
        return new ResponseEntity<>(issueAuthCookies(userDetails, servletResponse), HttpStatus.CREATED);
    }

    public ResponseEntity<JwtResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService.readRefreshToken(request)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Missing refresh token"));

        RefreshTokenService.RefreshTokenRotation rotation = refreshTokenService.rotate(refreshToken);
        UserDetails userDetails = userService.getUserByUserName(rotation.username());
        authCookieService.setAccessCookie(response, helper.generateToken(userDetails));
        authCookieService.setRefreshCookie(response, rotation.refreshToken());
        authCookieService.setCsrfCookie(response);

        JwtResponse jwtResponse = JwtResponse.builder()
                .username(userDetails.getUsername())
                .build();
        return new ResponseEntity<>(jwtResponse, HttpStatus.OK);
    }

    public ResponseEntity<ApiResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        authCookieService.readRefreshToken(request).ifPresent(refreshTokenService::revoke);
        authCookieService.clearAuthCookies(response);
        ApiResponse apiResponse = new ApiResponse("Logged out successfully", true, HttpStatus.OK.value(), HttpStatus.OK);
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    public ResponseEntity<ApiResponse> initiatePasswordReset(PasswordResetInitiateRequest request) {
        passwordResetService.initiate(request.getEmail());
        ApiResponse response = new ApiResponse(
                "If an account exists for this email, a password reset code has been sent.",
                true,
                HttpStatus.OK.value(),
                HttpStatus.OK
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<PasswordResetVerifyResponse> verifyPasswordReset(PasswordResetVerifyRequest request) {
        String resetToken = passwordResetService.verify(request.getEmail(), request.getOtp());
        return new ResponseEntity<>(new PasswordResetVerifyResponse(resetToken), HttpStatus.OK);
    }

    public ResponseEntity<ApiResponse> completePasswordReset(PasswordResetCompleteRequest request) {
        passwordResetService.complete(
                request.getEmail(),
                request.getResetToken(),
                request.getPassword(),
                request.getConfirmPassword());
        ApiResponse response = new ApiResponse("Password reset successfully", true, HttpStatus.OK.value(), HttpStatus.OK);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private void doAuthenticate(String email, String password) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, password);
        try {
            manager.authenticate(authentication);
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email);
            throw new BadCredentialsException(AppConstants.user.INVALID_CREDENTIAL);
        }
    }

    private JwtResponse issueAuthCookies(UserDetails userDetails, HttpServletResponse response) {
        String accessToken = helper.generateToken(userDetails);
        String refreshToken = refreshTokenService.create(userDetails.getUsername());
        authCookieService.setAccessCookie(response, accessToken);
        authCookieService.setRefreshCookie(response, refreshToken);
        authCookieService.setCsrfCookie(response);

        return JwtResponse.builder()
                .username(userDetails.getUsername())
                .build();
    }
}
