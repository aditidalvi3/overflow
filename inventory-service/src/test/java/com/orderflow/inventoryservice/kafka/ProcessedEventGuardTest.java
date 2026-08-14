package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.entity.ProcessedEvent;
import com.orderflow.inventoryservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedEventGuardTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private ProcessedEventGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProcessedEventGuard(processedEventRepository);
    }

    @Test
    void isDuplicate_freshEvent_insertSucceeds_returnsFalse() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        boolean duplicate = guard.isDuplicate("order.created", 1L);

        assertThat(duplicate).isFalse();
    }

    @Test
    void isDuplicate_redeliveredEvent_uniqueConstraintViolation_returnsTrue() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        boolean duplicate = guard.isDuplicate("order.created", 1L);

        assertThat(duplicate).isTrue();
    }
}
