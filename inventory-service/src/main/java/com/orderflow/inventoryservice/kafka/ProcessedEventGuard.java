package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.entity.ProcessedEvent;
import com.orderflow.inventoryservice.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Shared idempotent-consumer guard, per the contract: attempt an INSERT into
 * processed_events(topic, order_id) first; if it violates the (topic, order_id) unique
 * constraint, this is a redelivery - the caller should skip the business logic. Must be
 * invoked from within the same @Transactional as the caller's business logic (this class
 * intentionally has no @Transactional of its own so it always joins the caller's transaction),
 * and must flush eagerly so the constraint violation surfaces here rather than at commit time.
 */
@Component
public class ProcessedEventGuard {

    private final ProcessedEventRepository processedEventRepository;

    public ProcessedEventGuard(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public boolean isDuplicate(String topic, Long orderId) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(topic, orderId));
            return false;
        } catch (DataIntegrityViolationException ex) {
            return true;
        }
    }
}
