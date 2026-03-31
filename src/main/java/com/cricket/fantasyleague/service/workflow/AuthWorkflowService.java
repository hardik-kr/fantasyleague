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
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.jwtdto.JwtRequest;
import com.cricket.fantasyleague.payload.jwtdto.JwtResponse;
import com.cricket.fantasyleague.security.JwtHelper;
import com.cricket.fantasyleague.service.otp.EmailService;
import com.cricket.fantasyleague.service.otp.LoginAttemptService;
import com.cricket.fantasyleague.service.otp.OtpService;
import com.cricket.fantasyleague.service.user.UserService;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class AuthWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(AuthWorkflowService.class);

    private final UserService userService;
    private final AuthenticationManager manager;
    private final JwtHelper helper;
    private final OtpService otpService;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;

    public AuthWorkflowService(UserService userService,
                               AuthenticationManager manager,
                               JwtHelper helper,
                               OtpService otpService,
                               EmailService emailService,
                               LoginAttemptService loginAttemptService) {
        this.userService = userService;
        this.manager = manager;
        this.helper = helper;
        this.otpService = otpService;
        this.emailService = emailService;
        this.loginAttemptService = loginAttemptService;
    }

    public ResponseEntity<JwtResponse> login(JwtRequest request) {
        loginAttemptService.checkLocked(request.getEmail());
        doAuthenticate(request.getEmail(), request.getPassword());
        loginAttemptService.recordSuccess(request.getEmail());

        UserDetails userDetails = userService.getUserByUserName(request.getEmail());
        String token = helper.generateToken(userDetails);

        JwtResponse response = JwtResponse.builder()
                .jwttoken(token)
                .username(userDetails.getUsername())
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
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

    public ResponseEntity<ApiResponse> verifySignup(OtpVerifyRequest request) {
        OtpService.OtpEntry entry = otpService.validate(request.getEmail(), request.getOtp());
        userService.createUser(entry.userData());

        logger.info("Signup completed for email={}", request.getEmail());
        ApiResponse response = new ApiResponse(
                AppConstants.user.USER_CREATED_SUCCESS,
                true,
                HttpStatus.CREATED.value(),
                HttpStatus.CREATED
        );
        return new ResponseEntity<>(response, HttpStatus.CREATED);
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
}
