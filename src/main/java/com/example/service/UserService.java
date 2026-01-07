package com.example.service;

import com.example.db.entity.BalanceEntity;
import com.example.db.entity.UserEntity;
import com.example.db.repo.BalanceRepository;
import com.example.db.repo.UserRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.UUID;

@Singleton
public class UserService {

    private final UserRepository userRepository;
    private final BalanceRepository balanceRepository;

    public UserService(UserRepository userRepository, BalanceRepository balanceRepository) {
        this.userRepository = userRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public UUID getOrCreateUserId(long telegramUserId) {
        return userRepository.findByTelegramUserId(telegramUserId)
                .map(UserEntity::getId)
                .orElseGet(() -> createNewUser(telegramUserId));
    }

    private UUID createNewUser(long telegramUserId) {
        UserEntity user = new UserEntity(telegramUserId);
        UserEntity savedUser = userRepository.save(user);

        BalanceEntity balance = new BalanceEntity(savedUser.getId(), 0L, 0L);
        balanceRepository.save(balance);

        return savedUser.getId();
    }
}

