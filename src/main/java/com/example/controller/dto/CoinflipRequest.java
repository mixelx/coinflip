package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CoinflipRequest(
        String side,
        long stakeNano
) {
}

