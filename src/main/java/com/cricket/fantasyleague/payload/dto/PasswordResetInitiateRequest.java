package com.cricket.fantasyleague.payload.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetInitiateRequest {

    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Invalid email address")
    private String email;
}
