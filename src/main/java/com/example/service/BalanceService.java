package com.example.service;

import com.example.db.entity.BalanceEntity;
import com.example.db.repo.BalanceRepository;
import com.example.exception.BadRequestException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Singleton
public class BalanceService {

    private final BalanceRepository balanceRepository;

    public BalanceService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public BalanceDto getBalance(UUID userId) {
        BalanceEntity balance = balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Balance not found for user: " + userId));

        return new BalanceDto(
                balance.getUserId(),
                balance.getTonNano(),
                balance.getUsdtMicro()
        );
    }

    @Transactional
    public void credit(UUID userId, Asset asset, long amount) {
        BalanceEntity balance = balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Balance not found for user: " + userId));

        switch (asset) {
            case TON -> balance.setTonNano(balance.getTonNano() + amount);
            case USDT -> balance.setUsdtMicro(balance.getUsdtMicro() + amount);
        }

        balance.setUpdatedAt(OffsetDateTime.now());
        balanceRepository.update(balance);
    }

    @Transactional
    public void debit(UUID userId, Asset asset, long amount) {
        BalanceEntity balance = balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Balance not found for user: " + userId));

        long currentBalance = switch (asset) {
            case TON -> balance.getTonNano();
            case USDT -> balance.getUsdtMicro();
        };

        if (currentBalance < amount) {
            throw new BadRequestException("Insufficient " + asset + " balance. Required: " + amount + ", available: " + currentBalance);
        }

        switch (asset) {
            case TON -> balance.setTonNano(balance.getTonNano() - amount);
            case USDT -> balance.setUsdtMicro(balance.getUsdtMicro() - amount);
        }

        balance.setUpdatedAt(OffsetDateTime.now());
        balanceRepository.update(balance);
    }
}

