package com.marianna.gateway.repository;

import com.marianna.gateway.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(
            "SELECT e FROM OutboxEventEntity e WHERE e.published = false AND e.deadLettered = false AND e.parkedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findUnpublishedEvents(Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.published = true WHERE e.id = :eventId")
    void markAsPublished(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.id = :eventId")
    Optional<OutboxEventEntity> findByIdForUpdate(UUID eventId);

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.deadLettered = true WHERE e.id = :eventId")
    void markAsDeadLettered(UUID eventId);

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.parkedAt = CURRENT_TIMESTAMP WHERE e.id = :eventId")
    void markAsParked(UUID eventId);

    Optional<OutboxEventEntity> findByAggregateId(UUID aggregateId);
}
