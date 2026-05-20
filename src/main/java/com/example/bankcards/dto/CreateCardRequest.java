package com.example.bankcards.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCardRequest {

    @NotNull
    private Long ownerId;

    @NotBlank @Size(max = 100)
    private String cardHolderName;

    @NotNull @Future
    private LocalDate expiryDate;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal initialBalance;
}