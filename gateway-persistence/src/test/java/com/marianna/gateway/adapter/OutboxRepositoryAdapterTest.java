package com.marianna.gateway.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marianna.gateway.entity.OutboxEventEntity;
import com.marianna.gateway.repository.OutboxEventJpaRepository;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryAdapterTest {

    @Mock
    private OutboxEventJpaRepository jpa;

    private OutboxRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OutboxRepositoryAdapter(jpa);
    }

    @Test
    void recordFailureReturnsUpdatedAttemptCount() {
        UUID eventId = UUID.randomUUID();
        String error = "Kafka unavailable";
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAttempts(1);
        when(jpa.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        int attempts = adapter.recordFailure(eventId, error);

        assertEquals(2, attempts);
        assertEquals(2, event.getAttempts());
        assertEquals(error, event.getLastError());
        verify(jpa).findByIdForUpdate(eventId);
    }

    @Test
    void markAsDeadLetteredDelegatesToRepository() {
        UUID eventId = UUID.randomUUID();

        adapter.markAsDeadLettered(eventId);

        verify(jpa).markAsDeadLettered(eventId);
    }
}
