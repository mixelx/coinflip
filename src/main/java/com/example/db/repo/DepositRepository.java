package com.example.db.repo;

import com.example.db.entity.DepositEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface DepositRepository extends CrudRepository<DepositEntity, UUID> {

    Optional<DepositEntity> findByTxHash(String txHash);

    List<DepositEntity> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    List<DepositEntity> findByUserIdAndStatus(UUID userId, String status);
}
