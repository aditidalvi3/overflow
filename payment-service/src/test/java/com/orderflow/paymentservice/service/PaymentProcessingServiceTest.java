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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceTest {

    @Mock
    private PendingChargeRepository pendingChargeRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MockPaymentGateway paymentGateway;
    @Mock
    private PaymentEventProducer paymentEventProducer;

    private PaymentProcessingService service;

    private static final Long ORDER_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new PaymentProcessingService(pendingChargeRepository, processedEventRepository,
                paymentRepository, paymentGateway, paymentEventProducer);
    }

    // --- order.created upsert behavior ---

    @Test
    void upsertPendingCharge_newOrder_createsPendingCharge() {
        OrderCreatedEvent event = orderCreatedEvent(2000L, "tok_visa_4242");
        when(pendingChargeRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        service.upsertPendingCharge(event);

        ArgumentCaptor<PendingCharge> captor = ArgumentCaptor.forClass(PendingCharge.class);
        verify(pendingChargeRepository).save(captor.capture());
        PendingCharge saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getAmountCents()).isEqualTo(2000L);
        assertThat(saved.getPaymentToken()).isEqualTo("tok_visa_4242");
    }

    @Test
    void upsertPendingCharge_redeliveredEvent_updatesExistingRowInPlace() {
        PendingCharge existing = new PendingCharge(ORDER_ID, 1000L, "old_token");
        when(pendingChargeRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
        OrderCreatedEvent event = orderCreatedEvent(2500L, "new_token");

        service.upsertPendingCharge(event);

        ArgumentCaptor<PendingCharge> captor = ArgumentCaptor.forClass(PendingCharge.class);
        verify(pendingChargeRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getAmountCents()).isEqualTo(2500L);
        assertThat(existing.getPaymentToken()).isEqualTo("new_token");
    }

    // --- inventory.reserved charge-trigger behavior ---

    @Test
    void processCharge_gatewaySucceeds_savesPaymentAndPublishesProcessed() {
        InventoryReservedEvent event = inventoryReservedEvent();
        when(pendingChargeRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new PendingCharge(ORDER_ID, 2000L, null)));
        when(paymentGateway.charge(2000L, null))
                .thenReturn(new PaymentOutcome(PaymentStatus.SUCCEEDED, "mock_ref", null));
        Payment savedPayment = new Payment(ORDER_ID, 2000L, PaymentStatus.SUCCEEDED, "mock_ref", null);
        setId(savedPayment, 99L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        service.processCharge(event);

        verify(paymentRepository).save(any(Payment.class));
        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(paymentEventProducer).publishPaymentProcessed(captor.capture());
        verify(paymentEventProducer, never()).publishPaymentFailed(any());
        PaymentProcessedEvent published = captor.getValue();
        assertThat(published.orderId()).isEqualTo(ORDER_ID);
        assertThat(published.paymentId()).isEqualTo(99L);
        assertThat(published.amountCents()).isEqualTo(2000L);
        assertThat(published.providerRef()).isEqualTo("mock_ref");
        assertThat(published.correlationId()).isEqualTo(event.correlationId());
    }

    @Test
    void processCharge_gatewayFails_savesPaymentAndPublishesFailed() {
        InventoryReservedEvent event = inventoryReservedEvent();
        when(pendingChargeRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new PendingCharge(ORDER_ID, 2000L, "tok_fail")));
        when(paymentGateway.charge(2000L, "tok_fail"))
                .thenReturn(new PaymentOutcome(PaymentStatus.FAILED, "mock_ref", "Card declined by issuing bank"));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.processCharge(event);

        verify(paymentRepository).save(any(Payment.class));
        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentEventProducer).publishPaymentFailed(captor.capture());
        verify(paymentEventProducer, never()).publishPaymentProcessed(any());
        PaymentFailedEvent published = captor.getValue();
        assertThat(published.orderId()).isEqualTo(ORDER_ID);
        assertThat(published.status()).isEqualTo("FAILED");
        assertThat(published.reason()).isEqualTo("Card declined by issuing bank");
        assertThat(published.correlationId()).isEqualTo(event.correlationId());
    }

    @Test
    void processCharge_gatewayTimesOut_publishesFailedWithTimeoutStatus() {
        InventoryReservedEvent event = inventoryReservedEvent();
        when(pendingChargeRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new PendingCharge(ORDER_ID, 2000L, "tok_timeout")));
        when(paymentGateway.charge(2000L, "tok_timeout"))
                .thenReturn(new PaymentOutcome(PaymentStatus.TIMEOUT, "mock_ref", "Gateway did not respond within 250ms"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processCharge(event);

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentEventProducer).publishPaymentFailed(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("TIMEOUT");
    }

    @Test
    void processCharge_missingPendingCharge_logsAndSkipsGracefully() {
        InventoryReservedEvent event = inventoryReservedEvent();
        when(pendingChargeRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        service.processCharge(event);

        verifyNoInteractions(paymentGateway, paymentRepository, paymentEventProducer);
    }

    @Test
    void processCharge_duplicateDelivery_skipsBusinessLogic() {
        InventoryReservedEvent event = inventoryReservedEvent();
        when(processedEventRepository.save(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        service.processCharge(event);

        verifyNoInteractions(pendingChargeRepository, paymentGateway, paymentRepository, paymentEventProducer);
    }

    private static OrderCreatedEvent orderCreatedEvent(long totalCents, String paymentToken) {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), ORDER_ID, 1L, totalCents,
                paymentToken, List.of(), Instant.now());
    }

    private static InventoryReservedEvent inventoryReservedEvent() {
        return new InventoryReservedEvent(UUID.randomUUID(), UUID.randomUUID(), ORDER_ID, Instant.now());
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
