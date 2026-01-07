package com.example.db.entity;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@MappedEntity("deposit")
public class DepositEntity {

    @Id
    @GeneratedValue(GeneratedValue.Type.UUID)
    private UUID id;

    @MappedProperty("user_id")
    private UUID userId;

    private String asset;

    private Long amount;

    private String status;

    @MappedProperty("tx_hash")
    private String txHash;

    @MappedProperty("created_at")
    private OffsetDateTime createdAt;

    @MappedProperty("confirmed_at")
    private OffsetDateTime confirmedAt;

    public DepositEntity() {
    }

    public DepositEntity(UUID userId, String asset, Long amount, String status) {
        this.userId = userId;
        this.asset = asset;
        this.amount = amount;
        this.status = status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
