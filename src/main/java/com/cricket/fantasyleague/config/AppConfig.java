package com.cricket.fantasyleague.config ;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.cricket.fantasyleague.security.CricketServiceBearerRequestInterceptor;
import com.cricket.fantasyleague.service.user.UserService;


@Configuration
public class AppConfig
{
    @Autowired
    public UserService userService ;

    @Bean
    PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder() ;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration builder) throws Exception 
    {
        return builder.getAuthenticationManager();
    }

    @Bean
    RestTemplate restTemplate(
            @Value("${cricketapi.base-url:http://localhost:9090}") String cricketApiBaseUrl,
            @Value("${cricketapi.universal-auth.token:}") String cricketUniversalAuthToken) {
        RestTemplate rt = new RestTemplate();
        if (StringUtils.hasText(cricketUniversalAuthToken)) {
            rt.getInterceptors().add(
                    new CricketServiceBearerRequestInterceptor(cricketApiBaseUrl, cricketUniversalAuthToken));
        }
        return rt;
    }
}
