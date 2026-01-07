package com.example.controller.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record DepositClaimResponse(
        String status,
        UUID depositId,
        @Nullable String txHash,
        @Nullable Long newTonBalanceNano
) {
}

