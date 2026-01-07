package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WithdrawRequest(
        String asset,
        long amount,
        String toAddress
) {
}

