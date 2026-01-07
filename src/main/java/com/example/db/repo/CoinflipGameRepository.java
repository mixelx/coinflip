package com.example.db.repo;

import com.example.db.entity.CoinflipGameEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface CoinflipGameRepository extends CrudRepository<CoinflipGameEntity, UUID> {

    List<CoinflipGameEntity> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}

