package com.cricket.fantasyleague.payload.dto;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.cricket.fantasyleague.entity.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto implements UserDetails
{
    @NotEmpty(message = "Username must not be empty")
    @Size(min = 3, message = "Username must have at least 3 characters")
    private String username ;

    @NotEmpty(message = "First name must not be empty")
    @Size(min = 2, message = "First name must have at least 2 characters")
    private String firstname ;

    private String lastname ;

    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Invalid email address")
    private String email ;

    @NotEmpty(message = "Password cannot be empty")
    @Size(min = 7, max = 15, message = "Password length must between 7 and 15")
    private String password ;

    @NotEmpty(message = "Phone number cannot be empty")
    @Size(min = 10, max = 10, message = "Phone number must be length 10")
    private String phonenumber ;

    @NotEmpty(message = "Please enter your favourite team")
    private String favteam ;

    private UserRole role ;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name())) ;
    }
    @Override
    public String getUsername() {
        return this.username ;
    }
    @Override
    public boolean isAccountNonExpired() {
        return true ;
    }
    @Override
    public boolean isAccountNonLocked() {
        return true ;
    }
    @Override
    public boolean isCredentialsNonExpired() {
        return true ;
    }
    @Override
    public boolean isEnabled() {
        return true ;
    }    
}
