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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
    private final RestTemplate restTemplate;

    @Value("${swiftpay.ledger.base-url:http://localhost:8082}")
    private String ledgerBaseUrl;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Transactional
    @Override
    public PaymentResponse createPayment(
            PaymentRequest request
    ) {
        validateRequest(request);

        String idempotencyKey = request.idempotencyKey().trim();
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                "PROCESSING",
                IDEMPOTENCY_TTL
        );

        if (Boolean.FALSE.equals(acquired)) {
            throw new DuplicatePaymentException(
                    "Duplicate payment request for idempotency_key=" + idempotencyKey
            );
        }

        validateSenderBalance(request.senderId(), request.amount());

        String transactionId = "TXN-" + UUID.randomUUID();

        log.info(
                "Payment initiation received idempotencyKey={}, transactionId={}, senderId={}, receiverId={}, amount={}, currency={}",
                idempotencyKey,
                transactionId,
                request.senderId(),
                request.receiverId(),
                request.amount(),
                request.currency()
        );

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
                    "Payment creation failed idempotencyKey={}, transactionId={}",
                    idempotencyKey,
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

    @Transactional(readOnly = true)
    @Override
    public Page<PaymentResponse> getUserTransactions(
            Long userId,
            Pageable pageable
    ) {
        if (userId == null || userId <= 0) {
            throw new InvalidPaymentException(
                    "userId must be greater than zero"
            );
        }

        if (pageable == null) {
            throw new InvalidPaymentException(
                    "pageable must not be null"
            );
        }

        return paymentRepository
                .findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
                        userId,
                        userId,
                        pageable
                )
                .map(PaymentResponse::from);
    }

    private void validateSenderBalance(
            Long senderId,
            BigDecimal requestedAmount
    ) {
        if (senderId == null) {
            throw new InvalidPaymentException(
                    "sender_id is required"
            );
        }

        String balanceUrl = ledgerBaseUrl + "/api/accounts/" + senderId + "/balance";

        try {
            ResponseEntity<Map> response =
                    restTemplate.getForEntity(balanceUrl, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new InvalidPaymentException(
                        "Unable to validate sender balance"
                );
            }

            Object balanceValue = response.getBody().get("balance");
            if (balanceValue == null) {
                throw new InvalidPaymentException(
                        "Sender balance not available"
                );
            }

            BigDecimal currentBalance = new BigDecimal(balanceValue.toString());
            if (currentBalance.compareTo(requestedAmount) < 0) {
                throw new InvalidPaymentException(
                        "Insufficient sender balance"
                );
            }

            log.info(
                    "Sender balance validated. senderId={}, requestedAmount={}, availableBalance={}",
                    senderId,
                    requestedAmount,
                    currentBalance
            );

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new InvalidPaymentException(
                        "Sender account not found"
                );
            }
            if (ex.getStatusCode().value() == 422) {
                throw new InvalidPaymentException(
                        "Failed to validate sender balance"
                );
            }
            throw new InvalidPaymentException(
                    "Unable to validate sender balance"
            );
        } catch (RestClientException ex) {
            log.warn(
                    "Ledger balance lookup failed. senderId={}, ledgerBaseUrl={}",
                    senderId,
                    ledgerBaseUrl,
                    ex
            );
            throw new InvalidPaymentException(
                    "Unable to validate sender balance"
            );
        }
    }

    @Transactional
    @Override
    public void markFailed(
            String transactionId,
            String reason
    ) {

        Payment payment =
                paymentRepository
                        .findByTransactionId(transactionId)
                        .orElseThrow(() ->
                                new InvalidPaymentException(
                                        "Payment not found"
                                )
                        );

        if (payment.getStatus() == com.swiftpay.transactiongateway.domain.enums.PaymentStatus.FAILED) {
            log.info(
                    "Duplicate payment failure event ignored. transactionId={}, reason={}",
                    transactionId,
                    reason
            );
            return;
        }

        if (payment.getStatus() == com.swiftpay.transactiongateway.domain.enums.PaymentStatus.COMPLETED) {
            log.warn(
                    "Ignoring conflicting payment failure event for completed payment. transactionId={}, currentStatus={}, reason={}",
                    transactionId,
                    payment.getStatus(),
                    reason
            );
            return;
        }

        if (payment.getStatus() == com.swiftpay.transactiongateway.domain.enums.PaymentStatus.CANCELLED) {
            throw new InvalidPaymentException(
                    "Cannot fail a cancelled payment. transactionId=" + transactionId
            );
        }

        if (payment.getStatus() != com.swiftpay.transactiongateway.domain.enums.PaymentStatus.PROCESSING) {
            payment.markProcessing();
        }

        payment.markFailed(reason);

        log.info(
                "Payment marked as FAILED. transactionId={}, reason={}",
                transactionId,
                reason
        );
    }

    @Transactional
    @Override
    public void markCompleted(
            String transactionId
    ) {

        Payment payment =
                paymentRepository
                        .findByTransactionId(transactionId)
                        .orElseThrow(() ->
                                new InvalidPaymentException(
                                        "Payment not found"
                                )
                        );

        if (payment.getStatus() == com.swiftpay.transactiongateway.domain.enums.PaymentStatus.COMPLETED) {
            log.info(
                    "Duplicate payment completion event ignored. transactionId={}",
                    transactionId
            );
            return;
        }

        if (payment.getStatus() == com.swiftpay.transactiongateway.domain.enums.PaymentStatus.FAILED) {
            log.warn(
                    "Ignoring conflicting completion event for failed payment. transactionId={}, currentStatus={}",
                    transactionId,
                    payment.getStatus()
            );
            return;
        }

        if (payment.getStatus() == com.swiftpay.transactiongateway.domain.enums.PaymentStatus.CANCELLED) {
            throw new InvalidPaymentException(
                    "Cannot complete a cancelled payment. transactionId=" + transactionId
            );
        }

        if (payment.getStatus() != com.swiftpay.transactiongateway.domain.enums.PaymentStatus.PROCESSING) {
            payment.markProcessing();
        }

        payment.markCompleted();

        log.info(
                "Payment marked as COMPLETED. transactionId={}",
                transactionId
        );
    }

    private void validateRequest(
            PaymentRequest request
    ) {
        if (request == null) {
            throw new InvalidPaymentException(
                    "Payment request must not be null"
            );
        }

        if (request.idempotencyKey() == null ||
                request.idempotencyKey().isBlank()) {
            throw new InvalidPaymentException(
                    "idempotency_key must not be blank"
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
