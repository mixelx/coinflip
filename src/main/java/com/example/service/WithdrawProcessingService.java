package com.example.service;

import com.example.db.entity.WithdrawRequestEntity;
import com.example.db.repo.WithdrawRequestRepository;
import com.example.ton.TonPayoutClient;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for processing withdraw requests.
 * Handles claiming, sending TON, and status transitions.
 */
@Singleton
public class WithdrawProcessingService {

    private static final Logger LOG = LoggerFactory.getLogger(WithdrawProcessingService.class);

    private final WithdrawRequestRepository withdrawRepository;
    private final TonPayoutClient tonPayoutClient;
    private final BalanceService balanceService;
    private final int maxAttempts;

    public WithdrawProcessingService(
            WithdrawRequestRepository withdrawRepository,
            TonPayoutClient tonPayoutClient,
            BalanceService balanceService,
            @Value("${app.withdraw.max-attempts:3}") int maxAttempts) {
        this.withdrawRepository = withdrawRepository;
        this.tonPayoutClient = tonPayoutClient;
        this.balanceService = balanceService;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Claim a batch of CREATED withdraw requests for processing.
     * Atomically updates status to PROCESSING.
     *
     * @param limit Maximum number of requests to claim
     * @return List of claimed withdraw IDs
     */
    @Transactional
    public List<UUID> claimBatch(int limit) {
        // Find CREATED requests
        List<WithdrawRequestEntity> candidates = withdrawRepository
                .findByStatusOrderByCreatedAtAsc(WithdrawStatus.CREATED.name());

        if (candidates.isEmpty()) {
            LOG.debug("No withdraw requests to process");
            return List.of();
        }

        // Take only up to limit
        List<UUID> claimedIds = new ArrayList<>();
        int count = 0;

        for (WithdrawRequestEntity withdraw : candidates) {
            if (count >= limit) break;

            // Claim this withdraw
            withdraw.setStatus(WithdrawStatus.PROCESSING.name());
            withdraw.setAttempts(withdraw.getAttempts() != null ? withdraw.getAttempts() + 1 : 1);
            withdraw.setUpdatedAt(OffsetDateTime.now());
            withdraw.setLastError(null);
            withdrawRepository.update(withdraw);

            claimedIds.add(withdraw.getId());
            count++;
        }

        if (!claimedIds.isEmpty()) {
            LOG.info("Claimed {} withdraw requests for processing: {}", claimedIds.size(), claimedIds);
        }

        return claimedIds;
    }

    /**
     * Process a single withdraw request.
     * Attempts to send TON and updates status accordingly.
     *
     * @param withdrawId ID of the withdraw request
     */
    public void processOne(UUID withdrawId) {
        LOG.info("Processing withdraw: {}", withdrawId);

        WithdrawRequestEntity withdraw = withdrawRepository.findById(withdrawId).orElse(null);
        if (withdraw == null) {
            LOG.warn("Withdraw not found: {}", withdrawId);
            return;
        }

        // Skip if not in PROCESSING status (already processed or reverted)
        if (!WithdrawStatus.PROCESSING.name().equals(withdraw.getStatus())) {
            LOG.warn("Withdraw {} is not in PROCESSING status: {}", withdrawId, withdraw.getStatus());
            return;
        }

        // Validate asset
        if (!"TON".equals(withdraw.getAsset())) {
            LOG.error("Unsupported asset for withdraw {}: {}", withdrawId, withdraw.getAsset());
            handleFailure(withdrawId, new Exception("Only TON withdrawals are supported"));
            return;
        }

        // Check if payout client is ready
        if (!tonPayoutClient.isReady()) {
            LOG.error("TON payout client not ready for withdraw {}", withdrawId);
            handleFailure(withdrawId, new Exception("Payout service temporarily unavailable"));
            return;
        }

        try {
            // Send TON
            String txHash = tonPayoutClient.sendTon(withdraw.getToAddress(), withdraw.getAmount());
            
            // Mark as confirmed
            markConfirmed(withdrawId, txHash);
            
            LOG.info("Withdraw {} confirmed. txHash={}", withdrawId, txHash);

        } catch (Exception e) {
            LOG.error("Withdraw {} failed: {}", withdrawId, e.getMessage(), e);
            handleFailure(withdrawId, e);
        }
    }

    /**
     * Mark withdraw as confirmed with transaction hash.
     */
    @Transactional
    public void markConfirmed(UUID withdrawId, String txHash) {
        LOG.info("Marking withdraw {} as CONFIRMED. txHash={}", withdrawId, txHash);
        
        WithdrawRequestEntity withdraw = withdrawRepository.findById(withdrawId).orElse(null);
        if (withdraw == null) {
            LOG.warn("Cannot mark as confirmed - withdraw not found: {}", withdrawId);
            return;
        }

        withdraw.setStatus(WithdrawStatus.CONFIRMED.name());
        withdraw.setTxHash(txHash);
        withdraw.setProcessedAt(OffsetDateTime.now());
        withdraw.setUpdatedAt(OffsetDateTime.now());
        withdrawRepository.update(withdraw);
    }

    /**
     * Handle withdraw failure.
     * If max attempts reached, mark as FAILED and refund balance.
     * Otherwise, return to CREATED for retry.
     */
    @Transactional
    public void handleFailure(UUID withdrawId, Exception e) {
        WithdrawRequestEntity withdraw = withdrawRepository.findById(withdrawId).orElse(null);
        if (withdraw == null) {
            LOG.warn("Cannot handle failure - withdraw not found: {}", withdrawId);
            return;
        }

        String errorMessage = truncateError(e.getMessage());
        int attempts = withdraw.getAttempts() != null ? withdraw.getAttempts() : 0;

        LOG.warn("Withdraw {} failed. attempts={}, maxAttempts={}, error={}",
                withdrawId, attempts, maxAttempts, errorMessage);

        if (attempts >= maxAttempts) {
            // Final failure - refund balance
            LOG.info("Withdraw {} reached max attempts ({}). Marking as FAILED and refunding balance.", 
                    withdrawId, maxAttempts);
            
            withdraw.setStatus(WithdrawStatus.FAILED.name());
            withdraw.setLastError(errorMessage);
            withdraw.setProcessedAt(OffsetDateTime.now());
            withdraw.setUpdatedAt(OffsetDateTime.now());
            withdrawRepository.update(withdraw);
            
            // Refund balance to user
            try {
                balanceService.credit(withdraw.getUserId(), Asset.TON, withdraw.getAmount());
                LOG.info("Refunded {} nano TON to user {} for failed withdraw {}",
                        withdraw.getAmount(), withdraw.getUserId(), withdrawId);
            } catch (Exception refundError) {
                LOG.error("CRITICAL: Failed to refund balance for withdraw {}. User={}, Amount={}, Error={}",
                        withdrawId, withdraw.getUserId(), withdraw.getAmount(), refundError.getMessage());
            }

        } else {
            // Return to CREATED for retry
            LOG.info("Withdraw {} will be retried (attempt {}/{}). Returning to CREATED.", 
                    withdrawId, attempts, maxAttempts);
            
            withdraw.setStatus(WithdrawStatus.CREATED.name());
            withdraw.setLastError(errorMessage);
            withdraw.setUpdatedAt(OffsetDateTime.now());
            withdrawRepository.update(withdraw);
        }
    }

    /**
     * Recover stuck PROCESSING requests (crashed workers).
     * Called periodically to handle orphaned requests.
     */
    @Transactional
    public void recoverStuckProcessing() {
        LOG.debug("Running recovery for stuck PROCESSING withdraws...");
        
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);
        List<WithdrawRequestEntity> stuck = withdrawRepository.findStuckProcessing(cutoff);
        
        if (stuck.isEmpty()) {
            return;
        }

        LOG.warn("Found {} stuck PROCESSING withdraws, resetting to CREATED", stuck.size());
        
        for (WithdrawRequestEntity withdraw : stuck) {
            withdraw.setStatus(WithdrawStatus.CREATED.name());
            withdraw.setUpdatedAt(OffsetDateTime.now());
            withdrawRepository.update(withdraw);
            LOG.info("Reset stuck withdraw {} back to CREATED", withdraw.getId());
        }
    }

    /**
     * Truncate error message to prevent DB overflow.
     */
    private String truncateError(String error) {
        if (error == null) {
            return "Unknown error";
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
