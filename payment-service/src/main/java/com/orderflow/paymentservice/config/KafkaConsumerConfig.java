package com.orderflow.paymentservice.config;

import com.orderflow.paymentservice.kafka.event.InventoryReservedEvent;
import com.orderflow.paymentservice.kafka.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * order.created and inventory.reserved carry different payload shapes, so each needs its own
 * typed JsonDeserializer / listener container factory - Spring Boot's single autoconfigured
 * factory can only bind to one target type. Producers publish with
 * spring.json.add.type.headers=false (per the shared contract, no __TypeId__ header on the
 * wire), so each deserializer here is told its target type explicitly up front instead of
 * relying on a type header.
 */
@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;
    private final String groupId;

    public KafkaConsumerConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                                @Value("${spring.kafka.consumer.group-id}") String groupId) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedContainerFactory() {
        return containerFactory(OrderCreatedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> inventoryReservedContainerFactory() {
        return containerFactory(InventoryReservedEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(Class<T> targetType) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(targetType));
        return factory;
    }

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> targetType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<T> valueDeserializer = new JsonDeserializer<>(targetType);
        valueDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }
}
