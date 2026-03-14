package com.cricket.fantasyleague.exception;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.util.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CustomAccessDeniedException implements AccessDeniedHandler 
{
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ObjectMapper objectMapper = new ObjectMapper();
        ApiResponse resp = new ApiResponse(AppConstants.jwt.ACCESS_DENIED, false, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN) ;
        String jsonResponse = objectMapper.writeValueAsString(resp);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(jsonResponse);
    }
}