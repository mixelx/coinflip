package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CoinflipResponse(
        UUID id,
        String chosenSide,
        String resultSide,
        boolean win,
        long stakeNano
) {
}

