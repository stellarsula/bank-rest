package com.example.bankcards.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username обязателен")
        String username,

        @NotBlank(message = "Пароль обязателен")
        String password
) { }