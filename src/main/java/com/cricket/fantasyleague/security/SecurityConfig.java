package com.cricket.fantasyleague.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
    private OriginValidationFilter originValidationFilter;

    @Autowired
    private CsrfCookieFilter csrfCookieFilter;

    @Autowired
    private ApiRequestValidationFilter apiRequestValidationFilter;

    @Autowired
    private UserDetailsService userDetailsService ;

    @Autowired
    private PasswordEncoder passwordEncoder ;

    @Autowired
    private CustomAccessDeniedException customAccessDeniedException ;

    @Value("${security.allowed-origins:http://localhost:3000}")
    private String allowedOriginsCsv;

    @Bean
    @Profile("!disableSecurity")
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception 
    {
        http.csrf(csrf->csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth->auth.requestMatchers("/", "/health", "/auth/**").permitAll()
            .requestMatchers("/test/**", "/data/**", "/api/data/**", "/api/admin/**").hasAuthority(UserRole.ADMIN.name())
            .requestMatchers("/api/seasons/**").hasAnyAuthority(UserRole.USER.name())
            .requestMatchers("/api/daily/**").hasAnyAuthority(UserRole.USER.name())
            .anyRequest().authenticated())
            .exceptionHandling(ex->ex.authenticationEntryPoint(point)
                                    .accessDeniedHandler(customAccessDeniedException))
            .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) ;
        
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class) ;
        http.addFilterAfter(apiRequestValidationFilter, RateLimitFilter.class);
        http.addFilterAfter(originValidationFilter, ApiRequestValidationFilter.class);
        http.addFilterAfter(csrfCookieFilter, OriginValidationFilter.class);
        http.addFilterAfter(filter, CsrfCookieFilter.class) ;
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

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "X-Requested-With"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
