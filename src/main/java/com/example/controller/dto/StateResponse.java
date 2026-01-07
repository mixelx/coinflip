package com.example.controller.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record StateResponse(
        long tonBalanceNano,
        long usdtBalanceMicro,
        List<GameDto> games,
        List<WithdrawDto> withdraws
) {
}

