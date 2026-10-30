package com.marianna.gateway.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import com.marianna.gateway.entity.OutboxEventEntity;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.published = false AND e.deadLettered = false ORDER BY e.createdAt ASC")
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

    Optional<OutboxEventEntity> findByAggregateId(UUID aggregateId);

}