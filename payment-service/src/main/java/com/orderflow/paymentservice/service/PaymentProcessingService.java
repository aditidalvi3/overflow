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

        PendingCharge charge = awaitPendingCharge(event.orderId());
        if (charge == null) {
            // order.created and inventory.reserved are separate topics consumed on separate
            // threads, so same-key delivery only orders each individually - it does not
            // guarantee order.created's PendingCharge upsert has landed before inventory.reserved
            // is processed here. awaitPendingCharge() covers the normal race window; if it's
            // still missing after that, something upstream is genuinely broken - log loudly and
            // skip gracefully rather than throwing, since throwing would just trigger an endless
            // redelivery loop against the same gap.
            log.error("No PendingCharge found for orderId={} after waiting; cannot charge, skipping", event.orderId());
            return;
        }

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

    /**
     * Postgres' default READ COMMITTED isolation means each retry here sees a fresh snapshot, so
     * this will observe OrderCreatedListener's upsert as soon as it commits on its own thread.
     */
    private PendingCharge awaitPendingCharge(Long orderId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<PendingCharge> charge = pendingChargeRepository.findById(orderId);
            if (charge.isPresent()) {
                return charge.get();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return pendingChargeRepository.findById(orderId).orElse(null);
    }
}
