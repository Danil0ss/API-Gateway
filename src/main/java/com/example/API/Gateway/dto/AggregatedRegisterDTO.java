package com.example.API.Gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AggregatedRegisterDTO(
        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Name cannot be empty")
        String name,

        @NotBlank(message = "Surname cannot be empty")
        String surname,

        @NotBlank(message = "Birth date cannot be empty")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Birth date must be in format YYYY-MM-DD")
        String birthDate,

        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Invalid email format")
        String email
) {}