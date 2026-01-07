package com.example.db.repo;

import com.example.db.entity.BalanceEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface BalanceRepository extends CrudRepository<BalanceEntity, UUID> {

    Optional<BalanceEntity> findByUserId(UUID userId);
}

