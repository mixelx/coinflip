package com.example.service;

import com.example.config.AppConfig;
import com.example.db.entity.DepositEntity;
import com.example.db.repo.DepositRepository;
import com.example.ton.TonTransaction;
import com.example.ton.ToncenterClient;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for claiming deposits by verifying on-chain transactions
 */
@Singleton
public class DepositClaimService {

    private static final Logger LOG = LoggerFactory.getLogger(DepositClaimService.class);

    private final AppConfig appConfig;
    private final ToncenterClient toncenterClient;
    private final DepositService depositService;
    private final DepositRepository depositRepository;
    private final BalanceService balanceService;

    public DepositClaimService(
            AppConfig appConfig,
            ToncenterClient toncenterClient,
            DepositService depositService,
            DepositRepository depositRepository,
            BalanceService balanceService
    ) {
        this.appConfig = appConfig;
        this.toncenterClient = toncenterClient;
        this.depositService = depositService;
        this.depositRepository = depositRepository;
        this.balanceService = balanceService;
    }

    /**
     * Claim a deposit by verifying on-chain transaction
     *
     * @param userId     User ID
     * @param amountNano Amount in nanotons
     * @return ClaimResult with status and details
     */
    public ClaimResult claimDeposit(UUID userId, long amountNano) {
        // Create pending deposit
        UUID depositId = depositService.createPendingDeposit(userId, amountNano);
        LOG.info("Created pending deposit {} for user {} amount {}", depositId, userId, amountNano);

        // Get deposit address from config
        String depositAddress = appConfig.getDeposit().getTonAddress();
        int lookbackCount = appConfig.getTon().getVerify().getLookbackTxCount();

        // Fetch recent transactions
        List<TonTransaction> transactions = toncenterClient.getTransactions(depositAddress, lookbackCount);
        LOG.debug("Fetched {} transactions for address {}", transactions.size(), depositAddress);

        // Find matching transaction
        Optional<TonTransaction> matchingTx = findMatchingTransaction(transactions, depositAddress, amountNano);

        if (matchingTx.isPresent()) {
            TonTransaction tx = matchingTx.get();
            String txHash = tx.getHash();

            LOG.info("Found matching transaction {} for deposit {}", txHash, depositId);

            try {
                // Confirm the deposit
                depositService.confirmDeposit(depositId, txHash);

                // Get updated balance
                BalanceDto balance = balanceService.getBalance(userId);

                return new ClaimResult(
                        "CONFIRMED",
                        depositId,
                        txHash,
                        balance.tonNano()
                );
            } catch (Exception e) {
                LOG.error("Failed to confirm deposit {}: {}", depositId, e.getMessage());
                // Transaction might already be used
                return new ClaimResult("PENDING", depositId, null, null);
            }
        }

        LOG.info("No matching transaction found for deposit {}, status PENDING", depositId);
        return new ClaimResult("PENDING", depositId, null, null);
    }

    /**
     * Find a transaction matching the deposit criteria
     */
    private Optional<TonTransaction> findMatchingTransaction(
            List<TonTransaction> transactions,
            String depositAddress,
            long amountNano
    ) {
        for (TonTransaction tx : transactions) {
            if (tx.getInMsg() == null) {
                continue;
            }

            String destination = tx.getInMsg().getDestination();
            long value = tx.getInMsg().getValueNano();

            // Check if destination matches (normalize addresses for comparison)
            boolean destinationMatches = normalizeAddress(destination)
                    .equals(normalizeAddress(depositAddress));

            // Check if amount matches exactly
            boolean amountMatches = value == amountNano;

            if (destinationMatches && amountMatches) {
                // Check if this tx_hash is already used
                String txHash = tx.getHash();
                if (txHash != null && depositRepository.findByTxHash(txHash).isEmpty()) {
                    return Optional.of(tx);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Normalize TON address for comparison (simple version)
     */
    private String normalizeAddress(String address) {
        if (address == null) {
            return "";
        }
        // Remove any whitespace and convert to lowercase for comparison
        return address.trim().toLowerCase();
    }

    /**
     * Get deposit status
     */
    public Optional<DepositEntity> getDepositStatus(UUID depositId) {
        return depositRepository.findById(depositId);
    }

    public record ClaimResult(
            String status,
            UUID depositId,
            String txHash,
            Long newTonBalanceNano
    ) {
    }
}

