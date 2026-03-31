package com.cricket.fantasyleague.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.cricket.fantasyleague.config.DisableJwtFilter;
import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.exception.CustomAccessDeniedException;

@Configuration
@EnableWebSecurity
public class SecurityConfig 
{
    @Autowired
    private JwtAuthenticationEntryPoint point;
    
    @Autowired
    private JwtAuthenticationFilter filter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private UserDetailsService userDetailsService ;

    @Autowired
    private PasswordEncoder passwordEncoder ;

    @Autowired
    private CustomAccessDeniedException customAccessDeniedException ;

    @Bean
    @Profile("!disableSecurity")
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception 
    {
        http.csrf(csrf->csrf.disable())
            .cors(cors->cors.disable())
            .authorizeHttpRequests(auth->auth.requestMatchers("/auth/login","/auth/signup/**","/api/loadtest/**","/api/masterdata/**","/test/**").permitAll()
            .requestMatchers("/season/**").hasAnyAuthority(UserRole.USER.name())
            .anyRequest().authenticated())
            .exceptionHandling(ex->ex.authenticationEntryPoint(point)
                                    .accessDeniedHandler(customAccessDeniedException))
            .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) ;
        
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class) ;
        http.addFilterAfter(filter, RateLimitFilter.class) ;
        return http.build() ;
    }

    @Bean
    @Profile("disableSecurity")
    SecurityFilterChain disableSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/**").permitAll()) // Allow all requests
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Disable JWT authentication filter during development
        http.addFilterBefore(new DisableJwtFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }


    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider()
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService) ;
        provider.setPasswordEncoder(passwordEncoder);
        return provider ;
    }
}
