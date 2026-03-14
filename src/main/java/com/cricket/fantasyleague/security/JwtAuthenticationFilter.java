package com.cricket.fantasyleague.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cricket.fantasyleague.service.UserService;
import com.cricket.fantasyleague.util.AppConstants;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private Logger logger = LoggerFactory.getLogger(OncePerRequestFilter.class);
    
    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain filterChain) throws ServletException, IOException 
    {
        String requestHeader = request.getHeader("Authorization");
        //Bearer 2352345235sdfrsfgsdfsdf
        logger.info(" Header :  {}", requestHeader);
        String username = null;
        String token = null;
        if (requestHeader != null && requestHeader.startsWith("Bearer")) {
            //looking good
            token = requestHeader.substring(7);
            try {
                username = this.jwtHelper.getUsernameFromToken(token);
            } catch (IllegalArgumentException e) {
                logger.info(AppConstants.jwt.JWT_INVALID_USERNAME);
                request.setAttribute("error_msg",AppConstants.jwt.JWT_INVALID_USERNAME);
            } catch (ExpiredJwtException e) {
                logger.info(AppConstants.jwt.JWT_EXPIRED_TOKEN);
                request.setAttribute("error_msg",AppConstants.jwt.JWT_EXPIRED_TOKEN);
            } catch (MalformedJwtException e) {
                logger.info(AppConstants.jwt.JWT_INVALID_TOKEN);
                request.setAttribute("error_msg",AppConstants.jwt.JWT_INVALID_TOKEN);
            } catch (Exception e) {
                logger.info(AppConstants.jwt.JWT_SOMETHING_WENT_WRONG);
                request.setAttribute("error_msg",String.format(AppConstants.jwt.JWT_SOMETHING_WENT_WRONG,e.getMessage()));
            }
        } else {
            logger.info(AppConstants.jwt.JWT_INVALID_HEADER);
            request.setAttribute("error_msg",AppConstants.jwt.JWT_INVALID_HEADER);
        }
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) 
        {
            //fetch user detail from username
            try{
                UserDetails userDetails = this.userService.getUserByUserName(username) ;
                Boolean validateToken = this.jwtHelper.validateToken(token, userDetails);
                if (validateToken) 
                {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.info(AppConstants.jwt.JWT_VERIFICATION_FAILS);
                    request.setAttribute("error_msg",AppConstants.jwt.JWT_VERIFICATION_FAILS);
                }
            } catch(BadCredentialsException be) {
                logger.info(AppConstants.user.USER_NOT_FOUND);
                request.setAttribute("error_msg",String.format(AppConstants.user.USER_NOT_FOUND,username));
            } catch (Exception e) {
                logger.info(AppConstants.jwt.JWT_SOMETHING_WENT_WRONG);
                request.setAttribute("error_msg",AppConstants.jwt.JWT_SOMETHING_WENT_WRONG);
            }
        }
        filterChain.doFilter(request, response);
    }
}
