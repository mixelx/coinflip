package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
public record WithdrawDto(
        UUID id,
        String asset,
        long amount,
        String toAddress,
        String status,
        OffsetDateTime createdAt
) {
}

