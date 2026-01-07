package com.example.db.entity;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@MappedEntity("users")
public class UserEntity {

    @Id
    @GeneratedValue(GeneratedValue.Type.UUID)
    private UUID id;

    @MappedProperty("telegram_user_id")
    private Long telegramUserId;

    @MappedProperty("created_at")
    private OffsetDateTime createdAt;

    public UserEntity() {
    }

    public UserEntity(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

