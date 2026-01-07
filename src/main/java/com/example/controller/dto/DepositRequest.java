package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DepositRequest(
        String asset,
        long amount
) {
}

