package com.example.API.Gateway.dto;

public record AggregatedRegisterDTO(
        String password,
        String name,
        String surname,
        String birthDate,
        String email
) {}