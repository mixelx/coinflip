package com.example.service;

import com.example.db.entity.DepositEntity;
import com.example.db.repo.DepositRepository;
import com.example.exception.BadRequestException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DepositService {

    private static final Logger LOG = LoggerFactory.getLogger(DepositService.class);
    
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final DepositRepository depositRepository;
    private final BalanceService balanceService;

    public DepositService(DepositRepository depositRepository, BalanceService balanceService) {
        this.depositRepository = depositRepository;
        this.balanceService = balanceService;
        LOG.debug("DepositService initialized");
    }

    /**
     * Create a pending deposit (before on-chain confirmation)
     */
    @Transactional
    public UUID createPendingDeposit(UUID userId, long amount, String fromAddress, String asset) {
        LOG.debug(">>> createPendingDeposit: userId={}, amount={}, fromAddress={}, asset={}", 
                userId, amount, fromAddress, asset);
        
        if (amount <= 0) {
            LOG.warn("createPendingDeposit: invalid amount {} <= 0", amount);
            throw new BadRequestException("Amount must be positive");
        }

        String normalizedAsset = (asset == null || asset.isBlank()) ? "TON" : asset.toUpperCase();
        DepositEntity deposit = new DepositEntity(userId, normalizedAsset, amount, STATUS_PENDING);
        deposit.setCreatedAt(OffsetDateTime.now());
        
        if (fromAddress != null && !fromAddress.isBlank()) {
            deposit.setFromAddress(fromAddress.trim());
            LOG.debug("Set fromAddress: {}", fromAddress.trim());
        }
        
        LOG.debug("Saving pending {} deposit to database...", normalizedAsset);
        DepositEntity saved = depositRepository.save(deposit);
        
        LOG.debug("<<< createPendingDeposit: created deposit id={}", saved.getId());
        return saved.getId();
    }
    
    /**
     * Create a pending deposit (default to TON for backward compatibility)
     */
    @Transactional
    public UUID createPendingDeposit(UUID userId, long amount, String fromAddress) {
        return createPendingDeposit(userId, amount, fromAddress, "TON");
    }

    /**
     * Confirm a deposit after on-chain verification
     */
    @Transactional
    public void confirmDeposit(UUID depositId, String txHash) {
        LOG.debug(">>> confirmDeposit: depositId={}, txHash={}", depositId, txHash);
        
        if (txHash == null || txHash.isBlank()) {
            LOG.warn("confirmDeposit: txHash is null or blank");
            throw new BadRequestException("Transaction hash is required");
        }

        // Check if txHash already used
        LOG.debug("Checking if txHash {} is already used...", txHash);
        Optional<DepositEntity> existingTx = depositRepository.findByTxHash(txHash);
        if (existingTx.isPresent()) {
            LOG.warn("confirmDeposit: txHash {} already used by deposit {}", 
                    txHash, existingTx.get().getId());
            throw new BadRequestException("Transaction already processed: " + txHash);
        }

        // Find the pending deposit
        LOG.debug("Loading deposit {} from database...", depositId);
        DepositEntity deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> {
                    LOG.error("confirmDeposit: deposit {} not found", depositId);
                    return new BadRequestException("Deposit not found: " + depositId);
                });

        LOG.debug("Deposit loaded: status={}", deposit.getStatus());
        
        if (!STATUS_PENDING.equals(deposit.getStatus())) {
            LOG.warn("confirmDeposit: deposit {} is not PENDING, status={}", depositId, deposit.getStatus());
            throw new BadRequestException("Deposit is not in PENDING status: " + deposit.getStatus());
        }

        // Update deposit status
        LOG.debug("Updating deposit {} to CONFIRMED with txHash {}", depositId, txHash);
        deposit.setStatus(STATUS_CONFIRMED);
        deposit.setTxHash(txHash);
        deposit.setConfirmedAt(OffsetDateTime.now());
        depositRepository.update(deposit);

        // Credit the balance
        LOG.debug("Crediting balance for user {}: {} TON nano", deposit.getUserId(), deposit.getAmount());
        balanceService.credit(deposit.getUserId(), Asset.TON, deposit.getAmount());
        
        LOG.debug("<<< confirmDeposit: completed successfully");
    }

    /**
     * Reject a deposit (e.g., if verification failed)
     */
    @Transactional
    public void rejectDeposit(UUID depositId, String reason) {
        LOG.debug(">>> rejectDeposit: depositId={}, reason={}", depositId, reason);
        
        DepositEntity deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> {
                    LOG.error("rejectDeposit: deposit {} not found", depositId);
                    return new BadRequestException("Deposit not found: " + depositId);
                });

        if (!STATUS_PENDING.equals(deposit.getStatus())) {
            LOG.warn("rejectDeposit: deposit {} is not PENDING, status={}", depositId, deposit.getStatus());
            throw new BadRequestException("Deposit is not in PENDING status: " + deposit.getStatus());
        }

        LOG.debug("Updating deposit {} to REJECTED", depositId);
        deposit.setStatus(STATUS_REJECTED);
        depositRepository.update(deposit);
        
        LOG.debug("<<< rejectDeposit: completed");
    }

    /**
     * Legacy method for immediate deposit (for testing/manual credits)
     */
    @Transactional
    public DepositEntity recordDeposit(UUID userId, String assetStr, long amount, String txHash) {
        LOG.debug(">>> recordDeposit: userId={}, asset={}, amount={}, txHash={}", 
                userId, assetStr, amount, txHash);
        
        Asset asset = parseAsset(assetStr);

        if (amount <= 0) {
            LOG.warn("recordDeposit: invalid amount {} <= 0", amount);
            throw new BadRequestException("Amount must be positive");
        }

        // Check if txHash already used (if provided)
        if (txHash != null && !txHash.isBlank()) {
            LOG.debug("Checking if txHash {} is already used...", txHash);
            Optional<DepositEntity> existingTx = depositRepository.findByTxHash(txHash);
            if (existingTx.isPresent()) {
                LOG.warn("recordDeposit: txHash {} already used by deposit {}", 
                        txHash, existingTx.get().getId());
                throw new BadRequestException("Transaction already processed: " + txHash);
            }
        }

        // Credit the balance immediately
        LOG.debug("Crediting balance for user {}: {} {}", userId, amount, asset);
        balanceService.credit(userId, asset, amount);

        // Save the deposit record as CONFIRMED
        LOG.debug("Saving deposit record as CONFIRMED...");
        DepositEntity deposit = new DepositEntity(userId, assetStr, amount, STATUS_CONFIRMED);
        deposit.setTxHash(txHash);
        deposit.setCreatedAt(OffsetDateTime.now());
        deposit.setConfirmedAt(OffsetDateTime.now());
        DepositEntity saved = depositRepository.save(deposit);
        
        LOG.debug("<<< recordDeposit: created deposit id={}", saved.getId());
        return saved;
    }

    private Asset parseAsset(String assetStr) {
        LOG.trace("parseAsset: {}", assetStr);
        try {
            return Asset.valueOf(assetStr);
        } catch (IllegalArgumentException e) {
            LOG.warn("parseAsset: invalid asset '{}'", assetStr);
            throw new BadRequestException("Invalid asset: " + assetStr + ". Must be TON or USDT");
        }
    }
}
