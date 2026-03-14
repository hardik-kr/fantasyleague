package com.cricket.fantasyleague.payload.jwtdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JwtRequest 
{
    @Email(message = "Invalid email address !!") 
    private String email ;
    @NotEmpty(message = "Password cannot be empty !!")
    @Size(min = 7, message = "Password length atleast 7 characters")
    private String password ;    
}
