package com.example.service;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record BalanceDto(
        UUID userId,
        long tonNano,
        long usdtMicro
) {
}

