package com.orderflow.orderservice.kafka;

import com.orderflow.orderservice.kafka.event.InventoryReservationFailedEvent;
import com.orderflow.orderservice.kafka.event.PaymentFailedEvent;
import com.orderflow.orderservice.kafka.event.PaymentProcessedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * inventory.reservation-failed, payment.processed, and payment.failed each carry a differently
 * shaped payload, and producers publish without type headers (spring.json.add.type.headers=false
 * per the shared contract), so a single autoconfigured factory can't bind to all three - each
 * gets its own typed JsonDeserializer / listener container factory, referenced explicitly via
 * each @KafkaListener's containerFactory attribute. Same pattern already used by
 * inventory-service, payment-service, and notification-service.
 */
@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;
    private final String groupId;
    private final String autoOffsetReset;

    public KafkaConsumerConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                                @Value("${spring.kafka.consumer.group-id}") String groupId,
                                @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.autoOffsetReset = autoOffsetReset;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservationFailedEvent> inventoryReservationFailedContainerFactory() {
        return containerFactory(InventoryReservationFailedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentProcessedEvent> paymentProcessedContainerFactory() {
        return containerFactory(PaymentProcessedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedContainerFactory() {
        return containerFactory(PaymentFailedEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(Class<T> targetType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        JsonDeserializer<T> valueDeserializer = new JsonDeserializer<>(targetType, false);
        valueDeserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer);

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
