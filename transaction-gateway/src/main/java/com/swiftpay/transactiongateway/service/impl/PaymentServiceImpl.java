package com.swiftpay.transactiongateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.transactiongateway.config.KafkaConfig;
import com.swiftpay.transactiongateway.domain.dto.request.PaymentRequest;
import com.swiftpay.transactiongateway.domain.dto.response.PaymentResponse;
import com.swiftpay.transactiongateway.domain.entities.OutboxEvent;
import com.swiftpay.transactiongateway.domain.entities.Payment;
import com.swiftpay.transactiongateway.domain.outbox.PaymentInitiatedEvent;
import com.swiftpay.transactiongateway.exception.DuplicatePaymentException;
import com.swiftpay.transactiongateway.exception.InvalidPaymentException;
import com.swiftpay.transactiongateway.repository.OutboxEventRepository;
import com.swiftpay.transactiongateway.repository.PaymentRepository;
import com.swiftpay.transactiongateway.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final Duration IDEMPOTENCY_TTL =
            Duration.ofHours(24);

    private static final String IDEMPOTENCY_PREFIX =
            "swiftpay:idempotency:payment:";

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Override
    public PaymentResponse createPayment(
            PaymentRequest request
    ) {
        validateRequest(request);

        String transactionId = "TXN-" + UUID.randomUUID();

        log.info(
                "Payment initiation received transactionId={}, senderId={}, receiverId={}, amount={}, currency={}",
                transactionId,
                request.senderId(),
                request.receiverId(),
                request.amount(),
                request.currency()
        );

        Payment existing =
                paymentRepository.findByTransactionId(transactionId)
                        .orElse(null);

        if (existing != null) {
            log.info(
                    "Returning existing payment for transactionId={}, status={}",
                    transactionId,
                    existing.getStatus()
            );

            return PaymentResponse.from(existing);
        }

        String redisKey =
                IDEMPOTENCY_PREFIX + transactionId;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                "PROCESSING",
                IDEMPOTENCY_TTL
        );

        if (Boolean.FALSE.equals(acquired)) {
            Payment alreadyPersisted =
                    paymentRepository
                            .findByTransactionId(transactionId)
                            .orElse(null);

            if (alreadyPersisted != null) {
                return PaymentResponse.from(alreadyPersisted);
            }

            throw new DuplicatePaymentException(
                    "Payment request is already being processed"
            );
        }

        try {
            Payment payment = Payment.pending(
                    transactionId,
                    request.senderId(),
                    request.receiverId(),
                    request.amount(),
                    request.currency()
            );

            Payment savedPayment;

            try {
                savedPayment =
                        paymentRepository.saveAndFlush(payment);
            } catch (DataIntegrityViolationException ex) {

                log.warn(
                        "Duplicate payment detected at database level transactionId={}",
                        transactionId
                );

                Payment existingPayment =
                        paymentRepository
                                .findByTransactionId(transactionId)
                                .orElseThrow(() ->
                                        new DuplicatePaymentException(
                                                "Payment already exists"
                                        )
                                );

                return PaymentResponse.from(existingPayment);
            }

            PaymentInitiatedEvent event =
                    new PaymentInitiatedEvent(
                            UUID.randomUUID(),
                            savedPayment.getTransactionId(),
                            savedPayment.getSenderId(),
                            savedPayment.getReceiverId(),
                            savedPayment.getAmount(),
                            savedPayment.getCurrency()
                    );

            String payload = serialize(event);

            OutboxEvent outboxEvent =
                    OutboxEvent.pending(
                            event.eventId(),
                            "PAYMENT",
                            savedPayment.getTransactionId(),
                            "PaymentInitiated",
                            KafkaConfig.PAYMENT_INITIATED_TOPIC,
                            payload,
                            Instant.now()
                    );

            outboxEventRepository.save(outboxEvent);

            log.info(
                    "Payment created successfully transactionId={}, paymentId={}, outboxEventId={}",
                    savedPayment.getTransactionId(),
                    savedPayment.getId(),
                    event.eventId()
            );

            return PaymentResponse.from(savedPayment);

        } catch (RuntimeException ex) {

            redisTemplate.delete(redisKey);

            log.error(
                    "Payment creation failed transactionId={}",
                    transactionId,
                    ex
            );

            throw ex;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentResponse getPayment(
            String transactionId
    ) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new InvalidPaymentException(
                    "transactionId must not be blank"
            );
        }

        Payment payment =
                paymentRepository
                        .findByTransactionId(transactionId.trim())
                        .orElseThrow(() ->
                                new InvalidPaymentException(
                                        "Payment not found"
                                )
                        );

        return PaymentResponse.from(payment);
    }

    private void validateRequest(
            PaymentRequest request
    ) {
        if (request == null) {
            throw new InvalidPaymentException(
                    "Payment request must not be null"
            );
        }

        if (request.senderId().equals(request.receiverId())) {
            throw new InvalidPaymentException(
                    "senderId and receiverId must be different"
            );
        }

        if (request.amount() == null ||
                request.amount().signum() <= 0) {
            throw new InvalidPaymentException(
                    "amount must be greater than zero"
            );
        }

        if (request.amount().scale() > 4) {
            throw new InvalidPaymentException(
                    "amount must have at most 4 decimal places"
            );
        }

        if (request.currency() == null ||
                request.currency().isBlank() ||
                request.currency().trim().length() != 3) {
            throw new InvalidPaymentException(
                    "currency must contain exactly 3 characters"
            );
        }
    }

    private String serialize(
            PaymentInitiatedEvent event
    ) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Unable to serialize PaymentInitiated event",
                    ex
            );
        }
    }
}
