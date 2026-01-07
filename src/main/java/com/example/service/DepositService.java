package com.example.service;

import com.example.db.entity.DepositEntity;
import com.example.db.repo.DepositRepository;
import com.example.exception.BadRequestException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DepositService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final DepositRepository depositRepository;
    private final BalanceService balanceService;

    public DepositService(DepositRepository depositRepository, BalanceService balanceService) {
        this.depositRepository = depositRepository;
        this.balanceService = balanceService;
    }

    /**
     * Create a pending deposit (before on-chain confirmation)
     */
    @Transactional
    public UUID createPendingDeposit(UUID userId, long amountNano) {
        if (amountNano <= 0) {
            throw new BadRequestException("Amount must be positive");
        }

        DepositEntity deposit = new DepositEntity(userId, "TON", amountNano, STATUS_PENDING);
        deposit.setCreatedAt(OffsetDateTime.now());
        DepositEntity saved = depositRepository.save(deposit);
        return saved.getId();
    }

    /**
     * Confirm a deposit after on-chain verification
     */
    @Transactional
    public void confirmDeposit(UUID depositId, String txHash) {
        if (txHash == null || txHash.isBlank()) {
            throw new BadRequestException("Transaction hash is required");
        }

        // Check if txHash already used
        Optional<DepositEntity> existingTx = depositRepository.findByTxHash(txHash);
        if (existingTx.isPresent()) {
            throw new BadRequestException("Transaction already processed: " + txHash);
        }

        // Find the pending deposit
        DepositEntity deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new BadRequestException("Deposit not found: " + depositId));

        if (!STATUS_PENDING.equals(deposit.getStatus())) {
            throw new BadRequestException("Deposit is not in PENDING status: " + deposit.getStatus());
        }

        // Update deposit status
        deposit.setStatus(STATUS_CONFIRMED);
        deposit.setTxHash(txHash);
        deposit.setConfirmedAt(OffsetDateTime.now());
        depositRepository.update(deposit);

        // Credit the balance
        balanceService.credit(deposit.getUserId(), Asset.TON, deposit.getAmount());
    }

    /**
     * Reject a deposit (e.g., if verification failed)
     */
    @Transactional
    public void rejectDeposit(UUID depositId, String reason) {
        DepositEntity deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new BadRequestException("Deposit not found: " + depositId));

        if (!STATUS_PENDING.equals(deposit.getStatus())) {
            throw new BadRequestException("Deposit is not in PENDING status: " + deposit.getStatus());
        }

        deposit.setStatus(STATUS_REJECTED);
        depositRepository.update(deposit);
    }

    /**
     * Legacy method for immediate deposit (for testing/manual credits)
     */
    @Transactional
    public DepositEntity recordDeposit(UUID userId, String assetStr, long amount, String txHash) {
        Asset asset = parseAsset(assetStr);

        if (amount <= 0) {
            throw new BadRequestException("Amount must be positive");
        }

        // Check if txHash already used (if provided)
        if (txHash != null && !txHash.isBlank()) {
            Optional<DepositEntity> existingTx = depositRepository.findByTxHash(txHash);
            if (existingTx.isPresent()) {
                throw new BadRequestException("Transaction already processed: " + txHash);
            }
        }

        // Credit the balance immediately
        balanceService.credit(userId, asset, amount);

        // Save the deposit record as CONFIRMED
        DepositEntity deposit = new DepositEntity(userId, assetStr, amount, STATUS_CONFIRMED);
        deposit.setTxHash(txHash);
        deposit.setCreatedAt(OffsetDateTime.now());
        deposit.setConfirmedAt(OffsetDateTime.now());
        return depositRepository.save(deposit);
    }

    private Asset parseAsset(String assetStr) {
        try {
            return Asset.valueOf(assetStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid asset: " + assetStr + ". Must be TON or USDT");
        }
    }
}
