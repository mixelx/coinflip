package com.example.db.entity;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@MappedEntity("balance")
public class BalanceEntity {

    @Id
    @MappedProperty("user_id")
    private UUID userId;

    @MappedProperty("ton_nano")
    private Long tonNano;

    @MappedProperty("usdt_micro")
    private Long usdtMicro;

    @MappedProperty("updated_at")
    private OffsetDateTime updatedAt;

    public BalanceEntity() {
    }

    public BalanceEntity(UUID userId, Long tonNano, Long usdtMicro) {
        this.userId = userId;
        this.tonNano = tonNano;
        this.usdtMicro = usdtMicro;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Long getTonNano() {
        return tonNano;
    }

    public void setTonNano(Long tonNano) {
        this.tonNano = tonNano;
    }

    public Long getUsdtMicro() {
        return usdtMicro;
    }

    public void setUsdtMicro(Long usdtMicro) {
        this.usdtMicro = usdtMicro;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

