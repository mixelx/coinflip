package com.example.service;

import com.example.config.AppConfig;
import com.example.db.entity.DepositEntity;
import com.example.db.repo.DepositRepository;
import com.example.exception.BadRequestException;
import com.example.ton.TonPayoutClient;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.toncenter.TonCenter;
import org.ton.ton4j.toncenter.model.TransactionResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for claiming deposits by verifying on-chain transactions.
 * 
 * Flow:
 * 1. claimDeposit() creates a PENDING deposit and tries to verify once
 * 2. verifyPendingDeposit() can be called repeatedly to check blockchain
 * 3. When matching transaction found, deposit becomes CONFIRMED and balance is credited
 */
@Singleton
public class DepositClaimService {

    private static final Logger LOG = LoggerFactory.getLogger(DepositClaimService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private final AppConfig appConfig;
    private final TonPayoutClient tonPayoutClient;
    private final DepositService depositService;
    private final DepositRepository depositRepository;
    private final BalanceService balanceService;

    public DepositClaimService(
            AppConfig appConfig,
            TonPayoutClient tonPayoutClient,
            DepositService depositService,
            DepositRepository depositRepository,
            BalanceService balanceService
    ) {
        this.appConfig = appConfig;
        this.tonPayoutClient = tonPayoutClient;
        this.depositService = depositService;
        this.depositRepository = depositRepository;
        this.balanceService = balanceService;
        LOG.debug("DepositClaimService initialized");
    }

    /**
     * Claim a deposit by creating a PENDING record and attempting verification once.
     */
    public ClaimResult claimDeposit(UUID userId, long amount, String fromAddress, String asset) {
        LOG.debug(">>> claimDeposit called: userId={}, amount={}, fromAddress={}, asset={}", 
                userId, amount, fromAddress, asset);
        
        // Validate asset
        String normalizedAsset = (asset == null || asset.isBlank()) ? "TON" : asset.toUpperCase();
        if (!normalizedAsset.equals("TON") && !normalizedAsset.equals("USDT")) {
            throw new BadRequestException("Invalid asset: " + asset + ". Must be TON or USDT");
        }
        
        // Create pending deposit
        LOG.debug("Creating pending deposit in DB for asset {}...", normalizedAsset);
        UUID depositId = depositService.createPendingDeposit(userId, amount, fromAddress, normalizedAsset);
        LOG.info("Created pending deposit {} for user {} amount {} {} fromAddress {}",
                depositId, userId, amount, normalizedAsset, fromAddress);

        // Try to verify immediately (in case transaction is already visible)
        LOG.debug("Attempting immediate verification for deposit {}", depositId);
        VerifyResult verifyResult = verifyPendingDeposit(depositId);
        
        LOG.debug("<<< claimDeposit returning: status={}, depositId={}, txHash={}", 
                verifyResult.status(), depositId, verifyResult.txHash());

        return new ClaimResult(
                verifyResult.status(),
                depositId,
                verifyResult.txHash(),
                verifyResult.tonBalanceNano()
        );
    }

    /**
     * Verify a pending deposit by checking blockchain for matching transaction.
     * This method is idempotent - can be called multiple times safely.
     */
    @Transactional
    public VerifyResult verifyPendingDeposit(UUID depositId) {
        LOG.debug(">>> verifyPendingDeposit called: depositId={}", depositId);
        
        // Load deposit from DB
        LOG.debug("Loading deposit {} from database...", depositId);
        DepositEntity deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> {
                    LOG.error("Deposit not found in DB: {}", depositId);
                    return new BadRequestException("Deposit not found: " + depositId);
                });
        
        LOG.debug("Deposit loaded: id={}, userId={}, amount={}, asset={}, status={}, fromAddress={}, txHash={}",
                deposit.getId(), deposit.getUserId(), deposit.getAmount(), deposit.getAsset(),
                deposit.getStatus(), deposit.getFromAddress(), deposit.getTxHash());

        // If already confirmed, return current state
        if (!STATUS_PENDING.equals(deposit.getStatus())) {
            LOG.debug("Deposit {} already in status {}, skipping blockchain verification", 
                    depositId, deposit.getStatus());
            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            LOG.debug("<<< verifyPendingDeposit returning existing status: {}", deposit.getStatus());
            return new VerifyResult(
                    deposit.getStatus(),
                    deposit.getTxHash(),
                    balance.tonNano()
            );
        }

        // Route to appropriate verification method based on asset
        String asset = deposit.getAsset();
        if ("USDT".equals(asset)) {
            return verifyUsdtDeposit(deposit);
        }

        // Default: TON deposit verification
        // Get config
        String depositAddress = appConfig.getDeposit().getTonAddress();
        int lookbackCount = appConfig.getTon().getVerify().getLookbackTxCount();
        LOG.debug("Config: depositAddress={}, lookbackCount={}", depositAddress, lookbackCount);

        // Normalize expected deposit address using ton4j Address
        String depositAddressRaw;
        try {
            depositAddressRaw = Address.of(depositAddress).toRaw();
            LOG.debug("Normalized deposit address: {} -> {}", depositAddress, depositAddressRaw);
        } catch (Exception e) {
            LOG.error("Invalid deposit address in config: {} - {}", depositAddress, e.getMessage());
            return new VerifyResult(STATUS_PENDING, null, null);
        }

        LOG.info("=== VERIFYING DEPOSIT {} ===", depositId);
        LOG.info("Expected: address={}, amount={} nano, fromAddress={}", 
                depositAddressRaw, deposit.getAmount(), deposit.getFromAddress());

        // Fetch transactions from blockchain using TonCenter from TonPayoutClient
        TonCenter tonCenter = tonPayoutClient.getTonCenter();
        if (tonCenter == null) {
            LOG.error("TonCenter client not available");
            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
        }

        LOG.debug("Fetching transactions from TonCenter API...");
        List<TransactionResponse> transactions;
        try {
            long startTime = System.currentTimeMillis();
            var response = tonCenter.getTransactions(depositAddress, lookbackCount);
            long duration = System.currentTimeMillis() - startTime;
            
            if (response.isSuccess()) {
                transactions = response.getResult();
                LOG.debug("TonCenter API call completed in {}ms, received {} transactions", 
                        duration, transactions != null ? transactions.size() : 0);
            } else {
                LOG.error("TonCenter API error: {}", response.getError());
                BalanceDto balance = balanceService.getBalance(deposit.getUserId());
                return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch transactions from TonCenter: {}", e.getMessage(), e);
            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
        }

        if (transactions == null || transactions.isEmpty()) {
            LOG.warn("No transactions returned from TonCenter for address {}", depositAddress);
            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
        }

        // Find matching transaction
        LOG.debug("Searching for matching transaction among {} candidates...", transactions.size());
        Optional<TransactionResponse> matchingTx = findMatchingTransaction(
                transactions,
                depositAddressRaw,
                deposit.getAmount(),
                deposit.getFromAddress()
        );

        if (matchingTx.isPresent()) {
            TransactionResponse tx = matchingTx.get();
            String txHash = tx.getTransactionId() != null ? tx.getTransactionId().getHash() : null;

            LOG.info("!!! MATCH FOUND !!! Transaction {} matches deposit {}", txHash, depositId);

            // Double-check: is this txHash already used?
            LOG.debug("Checking if txHash {} is already used by another deposit...", txHash);
            Optional<DepositEntity> existingWithTxHash = depositRepository.findByTxHash(txHash);
            if (existingWithTxHash.isPresent()) {
                LOG.warn("Transaction {} already used by deposit {}, cannot confirm deposit {}",
                        txHash, existingWithTxHash.get().getId(), depositId);
                BalanceDto balance = balanceService.getBalance(deposit.getUserId());
                return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
            }

            // Confirm the deposit
            LOG.debug("Updating deposit {} status to CONFIRMED...", depositId);
            deposit.setStatus(STATUS_CONFIRMED);
            deposit.setTxHash(txHash);
            deposit.setConfirmedAt(OffsetDateTime.now());
            depositRepository.update(deposit);
            LOG.debug("Deposit {} updated in database", depositId);

            // Credit the balance
            LOG.debug("Crediting balance for user {}: +{} TON nano", deposit.getUserId(), deposit.getAmount());
            balanceService.credit(deposit.getUserId(), Asset.TON, deposit.getAmount());

            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            LOG.info("=== DEPOSIT {} CONFIRMED === txHash={}, newBalance={} TON nano",
                    depositId, txHash, balance.tonNano());

            return new VerifyResult(STATUS_CONFIRMED, txHash, balance.tonNano());
        }

        LOG.info("No matching transaction found for deposit {}, status remains PENDING", depositId);
        LOG.debug("Checked {} transactions, none matched criteria", transactions.size());
        BalanceDto balance = balanceService.getBalance(deposit.getUserId());
        LOG.debug("<<< verifyPendingDeposit returning PENDING, balance={}", balance.tonNano());
        return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
    }

    /**
     * Find a transaction matching the deposit criteria.
     * Logs detailed diagnostics for debugging.
     */
    private Optional<TransactionResponse> findMatchingTransaction(
            List<TransactionResponse> transactions,
            String expectedAddressRaw,
            long expectedAmountNano,
            String expectedFromAddress
    ) {
        LOG.debug(">>> findMatchingTransaction: looking for dest={}, amount={}, from={}", 
                expectedAddressRaw, expectedAmountNano, expectedFromAddress);
        
        // Normalize expected from address if provided using ton4j Address
        String expectedFromRaw = null;
        if (expectedFromAddress != null && !expectedFromAddress.isBlank()) {
            try {
                expectedFromRaw = Address.of(expectedFromAddress).toRaw();
                LOG.debug("Normalized fromAddress: {} -> {}", expectedFromAddress, expectedFromRaw);
            } catch (Exception e) {
                LOG.warn("Invalid fromAddress provided: {}, ignoring source check. Error: {}", 
                        expectedFromAddress, e.getMessage());
            }
        } else {
            LOG.debug("No fromAddress provided, will not check source");
        }

        LOG.debug("=== TRANSACTION SCAN START (total: {}) ===", transactions.size());
        
        int matchedDestCount = 0;
        int matchedAmountCount = 0;
        int matchedSourceCount = 0;
        
        for (int i = 0; i < transactions.size(); i++) {
            TransactionResponse tx = transactions.get(i);
            boolean shouldLog = i < 15; // Log first 15 transactions in detail

            TransactionResponse.Message inMsg = tx.getInMsg();
            if (inMsg == null) {
                if (shouldLog) {
                    String hash = tx.getTransactionId() != null ? tx.getTransactionId().getHash() : "unknown";
                    LOG.debug("TX[{}/{}] hash={}: NO IN_MSG (external out or internal)", 
                            i, transactions.size(), hash);
                }
                continue;
            }

            String destination = inMsg.getDestination();
            String source = inMsg.getSource();
            String valueStr = inMsg.getValue();
            long value = parseValue(valueStr);

            if (shouldLog) {
                String hash = tx.getTransactionId() != null ? tx.getTransactionId().getHash() : "unknown";
                LOG.debug("TX[{}/{}] hash={}: dest={}, value={}, source={}, utime={}", 
                        i, transactions.size(), hash, destination, value, source, tx.getUtime());
            }

            // Normalize destination using ton4j Address
            String destRaw;
            try {
                destRaw = Address.of(destination).toRaw();
            } catch (Exception e) {
                if (shouldLog) {
                    LOG.debug("  -> SKIP: cannot normalize destination '{}': {}", destination, e.getMessage());
                }
                continue;
            }

            // Check conditions
            boolean destinationMatches = destRaw.equals(expectedAddressRaw);
            boolean amountMatches = value == expectedAmountNano;

            if (destinationMatches) matchedDestCount++;
            if (amountMatches) matchedAmountCount++;

            // Check source if provided
            boolean sourceMatches = true;
            String sourceRaw = null;
            if (expectedFromRaw != null && source != null && !source.isBlank()) {
                try {
                    sourceRaw = Address.of(source).toRaw();
                    sourceMatches = sourceRaw.equals(expectedFromRaw);
                    if (sourceMatches) matchedSourceCount++;
                } catch (Exception e) {
                    if (shouldLog) {
                        LOG.debug("  -> Cannot normalize source '{}', skipping source check", source);
                    }
                    sourceMatches = true; // Can't normalize source, skip source check
                }
            }

            if (shouldLog) {
                LOG.debug("  -> destMatch={} ({}=={}), amountMatch={} ({}=={}), sourceMatch={} ({}=={})",
                        destinationMatches, destRaw, expectedAddressRaw,
                        amountMatches, value, expectedAmountNano,
                        sourceMatches, sourceRaw, expectedFromRaw);
            }

            if (destinationMatches && amountMatches && sourceMatches) {
                String txHash = tx.getTransactionId() != null ? tx.getTransactionId().getHash() : null;
                LOG.debug("  -> ALL CONDITIONS MET! Checking if txHash {} is unused...", txHash);
                
                // Check if tx_hash already used
                if (txHash != null && depositRepository.findByTxHash(txHash).isEmpty()) {
                    LOG.debug("  -> txHash {} is NOT used, returning as MATCH!", txHash);
                    LOG.debug("=== TRANSACTION SCAN END: MATCH FOUND at index {} ===", i);
                    return Optional.of(tx);
                } else {
                    LOG.debug("  -> txHash {} is ALREADY USED, continuing search...", txHash);
                }
            }
        }

        LOG.debug("=== TRANSACTION SCAN END: NO MATCH ===");
        LOG.debug("Stats: {} dest matches, {} amount matches, {} source matches out of {} transactions",
                matchedDestCount, matchedAmountCount, matchedSourceCount, transactions.size());

        return Optional.empty();
    }

    /**
     * Parse value string to long (nanotons).
     */
    private long parseValue(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Verify USDT (Jetton) deposit by checking Jetton transfers.
     * Uses TonCenter API v3 to find incoming Jetton transfers.
     */
    private VerifyResult verifyUsdtDeposit(DepositEntity deposit) {
        LOG.info("=== VERIFYING USDT DEPOSIT {} ===", deposit.getId());
        LOG.info("Expected: amount={} micro, fromAddress={}", deposit.getAmount(), deposit.getFromAddress());

        String depositAddress = appConfig.getDeposit().getTonAddress();
        String usdtJettonMaster = appConfig.getDeposit().getUsdtJettonMaster();
        
        if (usdtJettonMaster == null || usdtJettonMaster.isBlank()) {
            LOG.error("USDT Jetton Master address not configured");
            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
        }

        LOG.debug("USDT Jetton Master: {}, Deposit Address: {}", usdtJettonMaster, depositAddress);

        TonCenter tonCenter = tonPayoutClient.getTonCenter();
        if (tonCenter == null) {
            LOG.error("TonCenter client not available");
            BalanceDto balance = balanceService.getBalance(deposit.getUserId());
            return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
        }

        // For now, USDT verification requires more complex API calls
        // The TonCenter client from ton4j may not have Jetton transfer methods directly
        // This would need to use the raw API or a different approach
        // TODO: Implement USDT verification using ton4j or TonCenter API v3
        
        LOG.warn("USDT verification via ton4j TonCenter not fully implemented yet");
        BalanceDto balance = balanceService.getBalance(deposit.getUserId());
        return new VerifyResult(STATUS_PENDING, null, balance.tonNano());
    }

    /**
     * Get deposit status (read-only, no verification)
     */
    public Optional<DepositEntity> getDepositStatus(UUID depositId) {
        LOG.debug("getDepositStatus called: depositId={}", depositId);
        Optional<DepositEntity> result = depositRepository.findById(depositId);
        LOG.debug("getDepositStatus result: found={}", result.isPresent());
        return result;
    }

    /**
     * Check if deposit belongs to user
     */
    public boolean isDepositOwnedByUser(UUID depositId, UUID userId) {
        LOG.debug("isDepositOwnedByUser: depositId={}, userId={}", depositId, userId);
        boolean result = depositRepository.findById(depositId)
                .map(d -> {
                    boolean matches = d.getUserId().equals(userId);
                    LOG.debug("Deposit {} belongs to user {}, checking against {}: {}", 
                            depositId, d.getUserId(), userId, matches);
                    return matches;
                })
                .orElse(false);
        LOG.debug("isDepositOwnedByUser result: {}", result);
        return result;
    }

    public record ClaimResult(
            String status,
            UUID depositId,
            String txHash,
            Long newTonBalanceNano
    ) {
    }

    public record VerifyResult(
            String status,
            String txHash,
            Long tonBalanceNano
    ) {
    }
}
