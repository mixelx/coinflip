package com.example.ton;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Represents a Jetton (token) transfer on TON blockchain.
 */
@Serdeable
public record JettonTransfer(
        String transactionHash,
        long amount,
        @Nullable String sourceAddress,
        @Nullable String destinationAddress,
        long timestamp
) {
}

