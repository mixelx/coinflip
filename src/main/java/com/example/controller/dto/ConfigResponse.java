package com.example.controller.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ConfigResponse(
        String depositTonAddress,
        String network,
        @Nullable String usdtJettonMaster
) {
}
