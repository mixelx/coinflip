package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record DepositVerifyRequest(
        UUID depositId
) {
}

