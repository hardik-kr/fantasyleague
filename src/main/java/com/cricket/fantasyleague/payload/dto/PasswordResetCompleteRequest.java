package com.cricket.fantasyleague.payload.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetCompleteRequest {

    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Invalid email address")
    private String email;

    @NotEmpty(message = "Reset token cannot be empty")
    private String resetToken;

    @NotEmpty(message = "Password cannot be empty")
    @Size(min = 7, max = 15, message = "Password length must between 7 and 15")
    private String password;

    @NotEmpty(message = "Confirm password cannot be empty")
    @Size(min = 7, max = 15, message = "Password length must between 7 and 15")
    private String confirmPassword;
}
