package com.example.service;

import com.example.db.entity.WithdrawRequestEntity;
import com.example.db.repo.WithdrawRequestRepository;
import com.example.exception.BadRequestException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.UUID;

@Singleton
public class WithdrawService {

    private static final String STATUS_CREATED = "CREATED";

    private final WithdrawRequestRepository withdrawRequestRepository;
    private final BalanceService balanceService;

    public WithdrawService(WithdrawRequestRepository withdrawRequestRepository, BalanceService balanceService) {
        this.withdrawRequestRepository = withdrawRequestRepository;
        this.balanceService = balanceService;
    }

    @Transactional
    public WithdrawRequestEntity createWithdrawRequest(UUID userId, String assetStr, long amount, String toAddress) {
        Asset asset = parseAsset(assetStr);

        if (amount <= 0) {
            throw new BadRequestException("Amount must be positive");
        }

        if (toAddress == null || toAddress.isBlank()) {
            throw new BadRequestException("Destination address is required");
        }

        // Debit the balance
        balanceService.debit(userId, asset, amount);

        // Create withdraw request
        WithdrawRequestEntity request = new WithdrawRequestEntity(userId, assetStr, amount, toAddress, STATUS_CREATED);
        return withdrawRequestRepository.save(request);
    }

    private Asset parseAsset(String assetStr) {
        try {
            return Asset.valueOf(assetStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid asset: " + assetStr + ". Must be TON or USDT");
        }
    }
}

