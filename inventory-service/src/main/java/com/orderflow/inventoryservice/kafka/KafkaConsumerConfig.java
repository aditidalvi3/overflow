package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.OrderCancelledEvent;
import com.orderflow.inventoryservice.kafka.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.kafka.event.PaymentFailedEvent;
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
 * Each topic this service consumes carries a differently-shaped payload and the producer
 * does not add type headers (spring.json.add.type.headers: false per contract), so a single
 * generic JsonDeserializer can't infer the target type. Instead we build one
 * ConcurrentKafkaListenerContainerFactory per event type, each with a JsonDeserializer
 * hard-wired to that type (useHeadersIfPresent=false) and referenced explicitly via the
 * @KafkaListener's containerFactory attribute.
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
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedContainerFactory() {
        return containerFactory(OrderCreatedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedContainerFactory() {
        return containerFactory(PaymentFailedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> orderCancelledContainerFactory() {
        return containerFactory(OrderCancelledEvent.class);
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
