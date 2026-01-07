package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ConfigResponse(
        String depositTonAddress,
        String network
) {
}

