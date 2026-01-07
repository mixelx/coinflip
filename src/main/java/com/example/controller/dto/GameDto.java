package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
public record GameDto(
        UUID id,
        long stakeNano,
        String chosenSide,
        String resultSide,
        boolean win,
        OffsetDateTime createdAt
) {
}

