package com.example.service;

import com.example.db.entity.CoinflipGameEntity;
import com.example.db.repo.CoinflipGameRepository;
import com.example.exception.BadRequestException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Singleton
public class CoinflipService {

    private static final String HEADS = "HEADS";
    private static final String TAILS = "TAILS";

    private final CoinflipGameRepository coinflipGameRepository;
    private final BalanceService balanceService;
    private final SecureRandom random = new SecureRandom();

    public CoinflipService(CoinflipGameRepository coinflipGameRepository, BalanceService balanceService) {
        this.coinflipGameRepository = coinflipGameRepository;
        this.balanceService = balanceService;
    }

    @Transactional
    public CoinflipGameEntity play(UUID userId, String chosenSide, long stakeNano) {
        // Validate side
        if (!HEADS.equals(chosenSide) && !TAILS.equals(chosenSide)) {
            throw new BadRequestException("Invalid side: " + chosenSide + ". Must be HEADS or TAILS");
        }

        // Validate stake
        if (stakeNano <= 0) {
            throw new BadRequestException("Stake must be positive");
        }

        // Debit the stake
        balanceService.debit(userId, Asset.TON, stakeNano);

        // Flip the coin
        String resultSide = random.nextBoolean() ? HEADS : TAILS;
        boolean win = chosenSide.equals(resultSide);

        // If won, credit double the stake
        if (win) {
            balanceService.credit(userId, Asset.TON, stakeNano * 2);
        }

        // Save the game
        CoinflipGameEntity game = new CoinflipGameEntity(userId, stakeNano, chosenSide, resultSide, win);
        return coinflipGameRepository.save(game);
    }
}

