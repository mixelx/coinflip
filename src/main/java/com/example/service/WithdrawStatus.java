package com.example.service;

/**
 * Status of a withdraw request
 */
public enum WithdrawStatus {
    /**
     * Initial status - waiting for processing
     */
    CREATED,
    
    /**
     * Currently being processed by worker
     */
    PROCESSING,
    
    /**
     * Successfully sent to blockchain
     */
    CONFIRMED,
    
    /**
     * Failed after max retries, balance refunded
     */
    FAILED
}


