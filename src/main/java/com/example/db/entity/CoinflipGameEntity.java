package com.example.db.entity;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@MappedEntity("coinflip_game")
public class CoinflipGameEntity {

    @Id
    @GeneratedValue(GeneratedValue.Type.UUID)
    private UUID id;

    @MappedProperty("user_id")
    private UUID userId;

    @MappedProperty("stake_nano")
    private Long stakeNano;

    @MappedProperty("chosen_side")
    private String chosenSide;

    @MappedProperty("result_side")
    private String resultSide;

    private Boolean win;

    @MappedProperty("created_at")
    private OffsetDateTime createdAt;

    public CoinflipGameEntity() {
    }

    public CoinflipGameEntity(UUID userId, Long stakeNano, String chosenSide, String resultSide, Boolean win) {
        this.userId = userId;
        this.stakeNano = stakeNano;
        this.chosenSide = chosenSide;
        this.resultSide = resultSide;
        this.win = win;
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

    public Long getStakeNano() {
        return stakeNano;
    }

    public void setStakeNano(Long stakeNano) {
        this.stakeNano = stakeNano;
    }

    public String getChosenSide() {
        return chosenSide;
    }

    public void setChosenSide(String chosenSide) {
        this.chosenSide = chosenSide;
    }

    public String getResultSide() {
        return resultSide;
    }

    public void setResultSide(String resultSide) {
        this.resultSide = resultSide;
    }

    public Boolean getWin() {
        return win;
    }

    public void setWin(Boolean win) {
        this.win = win;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

