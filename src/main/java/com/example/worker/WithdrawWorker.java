package com.example.worker;

import com.example.service.WithdrawProcessingService;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled worker for processing withdraw requests.
 * Runs periodically to claim and process pending withdrawals.
 */
@Singleton
public class WithdrawWorker {

    private static final Logger LOG = LoggerFactory.getLogger(WithdrawWorker.class);

    private final WithdrawProcessingService processingService;
    private final int batchSize;

    public WithdrawWorker(
            WithdrawProcessingService processingService,
            @Value("${app.withdraw.worker-batch-size:5}") int batchSize) {
        this.processingService = processingService;
        this.batchSize = batchSize;
    }

    /**
     * Main worker task - processes withdraw requests.
     * Runs every 3 seconds by default.
     */
    @Scheduled(fixedDelay = "${app.withdraw.worker-interval-ms:3000}ms")
    public void processWithdraws() {
        try {
            // Claim batch of CREATED requests
            List<UUID> ids = processingService.claimBatch(batchSize);

            if (ids.isEmpty()) {
                return; // Nothing to process
            }

            LOG.info("Claimed {} withdraw requests for processing", ids.size());

            // Process each withdraw sequentially
            for (UUID id : ids) {
                try {
                    processingService.processOne(id);
                } catch (Exception e) {
                    LOG.error("Error processing withdraw {}: {}", id, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            LOG.error("Error in withdraw worker: {}", e.getMessage(), e);
        }
    }

    /**
     * Recovery task - resets stuck PROCESSING requests.
     * Runs every 60 seconds by default.
     */
    @Scheduled(fixedDelay = "${app.withdraw.recovery-interval-ms:60000}ms")
    public void recoverStuckWithdraws() {
        try {
            processingService.recoverStuckProcessing();
        } catch (Exception e) {
            LOG.error("Error in withdraw recovery: {}", e.getMessage(), e);
        }
    }
}


