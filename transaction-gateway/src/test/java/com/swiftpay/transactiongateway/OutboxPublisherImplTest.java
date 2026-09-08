package com.swiftpay.transactiongateway;

import com.swiftpay.transactiongateway.domain.entities.OutboxEvent;
import com.swiftpay.transactiongateway.domain.enums.OutboxStatus;
import com.swiftpay.transactiongateway.repository.OutboxEventRepository;
import com.swiftpay.transactiongateway.service.impl.OutboxPublisherImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OutboxPublisherImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxPublisherImpl publisher;

    @Test
    public void shouldMarkEventPublishedAfterSuccessfulKafkaSend() throws Exception {
        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "PAYMENT",
                "TXN-123",
                "PaymentInitiated",
                "swiftpay.payment.initiated",
                "{\"transactionId\":\"TXN-123\"}",
                Instant.now()
        );

        when(outboxEventRepository.findAvailableEvents(
                eq(OutboxStatus.PENDING),
                any(Instant.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(new SendResult<>(null, null));

        when(kafkaTemplate.send(
                eq("swiftpay.payment.initiated"),
                eq("TXN-123"),
                eq(event.getPayload())
        )).thenReturn(future);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository, atLeastOnce()).saveAndFlush(event);
    }

    @Test
    public void shouldKeepEventPendingAndRetryAfterKafkaFailure() throws Exception {
        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "PAYMENT",
                "TXN-123",
                "PaymentInitiated",
                "swiftpay.payment.initiated",
                "{\"transactionId\":\"TXN-123\"}",
                Instant.now()
        );

        when(outboxEventRepository.findAvailableEvents(
                eq(OutboxStatus.PENDING),
                any(Instant.class),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker down"));

        when(kafkaTemplate.send(
                eq("swiftpay.payment.initiated"),
                eq("TXN-123"),
                eq(event.getPayload())
        )).thenReturn(future);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getAvailableAt()).isAfter(Instant.now());
        verify(outboxEventRepository, atLeastOnce()).saveAndFlush(event);
    }
}
