package com.example.controller.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DepositClaimRequest(
        long amountNano,
        @Nullable String fromAddress
) {
}
