package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record WithdrawResponse(
        UUID id,
        String asset,
        long amount,
        String toAddress,
        String status
) {
}

