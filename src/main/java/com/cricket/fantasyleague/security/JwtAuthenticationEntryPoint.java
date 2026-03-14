package com.cricket.fantasyleague.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint 
{
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException 
    {
        // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // PrintWriter writer = response.getWriter();
        // writer.println("Access Denied !! " + authException.getMessage()+request.getAttribute("error_msg"));
        ObjectMapper objectMapper = new ObjectMapper();
        ApiResponse resp = new ApiResponse((String)request.getAttribute("error_msg"), false, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED) ;
        String jsonResponse = objectMapper.writeValueAsString(resp);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(jsonResponse);
    }
}
