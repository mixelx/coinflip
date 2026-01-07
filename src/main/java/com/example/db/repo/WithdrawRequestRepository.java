package com.example.db.repo;

import com.example.db.entity.WithdrawRequestEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface WithdrawRequestRepository extends CrudRepository<WithdrawRequestEntity, UUID> {

    List<WithdrawRequestEntity> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find CREATED withdraw requests for processing.
     * NOTE: For MVP, we don't use FOR UPDATE SKIP LOCKED (requires raw JDBC).
     * In production with multiple workers, consider using JdbcOperations directly.
     */
    List<WithdrawRequestEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Recovery: find stuck PROCESSING requests.
     */
    @Query(value = "SELECT * FROM withdraw_request WHERE status = 'PROCESSING' AND updated_at < :cutoff", nativeQuery = true)
    List<WithdrawRequestEntity> findStuckProcessing(java.time.OffsetDateTime cutoff);
}
