package com.services.event;

import com.services.entity.OutboxEvent;
import com.services.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private BookingEventProducer bookingEventProducer;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private OutboxEvent unpublishedEvent() {
        return new OutboxEvent(1L, 5L, 10L, List.of(20L), "customer@example.com", "+15550001111", false, LocalDateTime.now());
    }

    @Test
    void successfulPublishMarksRowPublished() {
        OutboxEvent event = unpublishedEvent();
        when(bookingEventProducer.publish(any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelay.relayOne(event);

        assertTrue(event.isPublished());
        verify(outboxEventRepository, times(1)).save(event);
    }

    @Test
    void failedPublishLeavesRowUnpublishedAndDoesNotThrow() {
        OutboxEvent event = unpublishedEvent();
        when(bookingEventProducer.publish(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unreachable")));

        outboxRelay.relayOne(event);

        assertFalse(event.isPublished());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void oneFailureDoesNotStopTheRestOfTheBatch() {
        OutboxEvent failing = unpublishedEvent();
        OutboxEvent succeeding = unpublishedEvent();
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(failing, succeeding));
        when(bookingEventProducer.publish(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unreachable")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelay.relayUnpublishedEvents();

        assertFalse(failing.isPublished());
        assertTrue(succeeding.isPublished());
        verify(outboxEventRepository, times(1)).save(succeeding);
    }
}
