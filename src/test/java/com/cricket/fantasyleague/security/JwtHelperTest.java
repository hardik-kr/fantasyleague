package com.cricket.fantasyleague.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

class JwtHelperTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void generatedTokensCarryAccessTypeAndValidate() {
        JwtHelper helper = helper();
        UserDetails user = User.withUsername("user@example.com").password("x").authorities("USER").build();

        String token = helper.generateToken(user);

        assertThat(helper.getTokenTypeFromToken(token)).isEqualTo("access");
        assertThat(helper.validateToken(token, user)).isTrue();
    }

    @Test
    void tokenForAnotherUserDoesNotValidate() {
        JwtHelper helper = helper();
        UserDetails owner = User.withUsername("owner@example.com").password("x").authorities("USER").build();
        UserDetails other = User.withUsername("other@example.com").password("x").authorities("USER").build();

        assertThat(helper.validateToken(helper.generateToken(owner), other)).isFalse();
    }

    private JwtHelper helper() {
        JwtHelper helper = new JwtHelper();
        ReflectionTestUtils.setField(helper, "secret", SECRET);
        ReflectionTestUtils.setField(helper, "accessTokenValiditySeconds", 900L);
        return helper;
    }
}
