package com.example.controller.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record DepositVerifyResponse(
        UUID depositId,
        String status,
        @Nullable String txHash,
        long tonBalanceNano
) {
}

