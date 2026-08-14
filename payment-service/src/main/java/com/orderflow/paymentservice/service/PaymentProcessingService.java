package com.orderflow.paymentservice.service;

import com.orderflow.paymentservice.entity.Payment;
import com.orderflow.paymentservice.entity.PaymentStatus;
import com.orderflow.paymentservice.entity.PendingCharge;
import com.orderflow.paymentservice.entity.ProcessedEvent;
import com.orderflow.paymentservice.kafka.PaymentEventProducer;
import com.orderflow.paymentservice.kafka.event.InventoryReservedEvent;
import com.orderflow.paymentservice.kafka.event.OrderCreatedEvent;
import com.orderflow.paymentservice.kafka.event.PaymentFailedEvent;
import com.orderflow.paymentservice.kafka.event.PaymentProcessedEvent;
import com.orderflow.paymentservice.repository.PaymentRepository;
import com.orderflow.paymentservice.repository.PendingChargeRepository;
import com.orderflow.paymentservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private static final String TOPIC_INVENTORY_RESERVED = "inventory.reserved";

    private final PendingChargeRepository pendingChargeRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentProcessingService(PendingChargeRepository pendingChargeRepository,
                                     ProcessedEventRepository processedEventRepository,
                                     PaymentRepository paymentRepository,
                                     MockPaymentGateway paymentGateway,
                                     PaymentEventProducer paymentEventProducer) {
        this.pendingChargeRepository = pendingChargeRepository;
        this.processedEventRepository = processedEventRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.paymentEventProducer = paymentEventProducer;
    }

    /**
     * order.created is a pure cache-fill (it doesn't charge anything or produce further events),
     * and orderId is the PendingCharge primary key, so a plain upsert-by-primary-key is already
     * idempotent on its own - redelivering the same order.created just overwrites the row with
     * the same values. No processed_events guard needed for this listener.
     */
    @Transactional
    public void upsertPendingCharge(OrderCreatedEvent event) {
        PendingCharge charge = pendingChargeRepository.findById(event.orderId())
                .orElseGet(() -> new PendingCharge(event.orderId(), event.totalCents(), event.paymentToken()));
        charge.setAmountCents(event.totalCents());
        charge.setPaymentToken(event.paymentToken());
        charge.setReceivedAt(Instant.now());
        pendingChargeRepository.save(charge);
    }

    /**
     * inventory.reserved is the trigger to actually charge money, so it's guarded by the
     * processed_events idempotent-consumer pattern from the contract.
     */
    @Transactional
    public void processCharge(InventoryReservedEvent event) {
        try {
            processedEventRepository.save(new ProcessedEvent(TOPIC_INVENTORY_RESERVED, event.orderId()));
        } catch (DataIntegrityViolationException e) {
            log.info("duplicate delivery, skipping. topic={} orderId={}", TOPIC_INVENTORY_RESERVED, event.orderId());
            return;
        }

        Optional<PendingCharge> maybeCharge = pendingChargeRepository.findById(event.orderId());
        if (maybeCharge.isEmpty()) {
            // Eventual-consistency edge case: order.created and inventory.reserved are produced
            // by different services but keyed by the same orderId, so same-partition delivery
            // should guarantee order.created lands first. If the PendingCharge is still missing
            // here, something upstream is out of order (or was replayed strangely) - log loudly
            // and skip gracefully rather than throwing, since throwing would just trigger an
            // endless redelivery loop against the same gap.
            log.error("No PendingCharge found for orderId={}; cannot charge, skipping", event.orderId());
            return;
        }
        PendingCharge charge = maybeCharge.get();

        PaymentOutcome outcome = paymentGateway.charge(charge.getAmountCents(), charge.getPaymentToken());
        Payment payment = paymentRepository.save(new Payment(event.orderId(), charge.getAmountCents(),
                outcome.status(), outcome.providerRef(), outcome.failureReason()));

        if (outcome.status() == PaymentStatus.SUCCEEDED) {
            paymentEventProducer.publishPaymentProcessed(new PaymentProcessedEvent(
                    UUID.randomUUID(), event.correlationId(), event.orderId(), payment.getId(),
                    charge.getAmountCents(), outcome.providerRef(), Instant.now()));
        } else {
            paymentEventProducer.publishPaymentFailed(new PaymentFailedEvent(
                    UUID.randomUUID(), event.correlationId(), event.orderId(), outcome.status().name(),
                    outcome.failureReason(), Instant.now()));
        }
    }
}
