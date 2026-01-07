package com.example.db.entity;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@MappedEntity("withdraw_request")
public class WithdrawRequestEntity {

    @Id
    @GeneratedValue(GeneratedValue.Type.UUID)
    private UUID id;

    @MappedProperty("user_id")
    private UUID userId;

    private String asset;

    private Long amount;

    @MappedProperty("to_address")
    private String toAddress;

    private String status;

    private Integer attempts;

    @MappedProperty("last_error")
    private String lastError;

    @MappedProperty("tx_hash")
    private String txHash;

    @MappedProperty("created_at")
    private OffsetDateTime createdAt;

    @MappedProperty("updated_at")
    private OffsetDateTime updatedAt;

    @MappedProperty("processed_at")
    private OffsetDateTime processedAt;

    public WithdrawRequestEntity() {
    }

    public WithdrawRequestEntity(UUID userId, String asset, Long amount, String toAddress, String status) {
        this.userId = userId;
        this.asset = asset;
        this.amount = amount;
        this.toAddress = toAddress;
        this.status = status;
        this.attempts = 0;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
