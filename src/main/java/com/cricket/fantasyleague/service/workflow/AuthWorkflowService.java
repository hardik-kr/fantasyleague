package com.cricket.fantasyleague.service.workflow;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.jwtdto.JwtRequest;
import com.cricket.fantasyleague.payload.jwtdto.JwtResponse;
import com.cricket.fantasyleague.security.JwtHelper;
import com.cricket.fantasyleague.service.UserService;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class AuthWorkflowService {

    private final UserService userService;
    private final AuthenticationManager manager;
    private final JwtHelper helper;

    public AuthWorkflowService(UserService userService,
                               AuthenticationManager manager,
                               JwtHelper helper) {
        this.userService = userService;
        this.manager = manager;
        this.helper = helper;
    }

    public ResponseEntity<JwtResponse> login(JwtRequest request) {
        doAuthenticate(request.getEmail(), request.getPassword());

        UserDetails userDetails = userService.getUserByUserName(request.getEmail());
        String token = helper.generateToken(userDetails);

        JwtResponse response = JwtResponse.builder()
                .jwttoken(token)
                .username(userDetails.getUsername())
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<ApiResponse> signup(UserDto request) {
        userService.createUser(request);
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
            throw new BadCredentialsException(AppConstants.user.INVALID_CREDENTIAL);
        }
    }
}
