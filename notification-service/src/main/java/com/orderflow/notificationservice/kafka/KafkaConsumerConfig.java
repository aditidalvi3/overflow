package com.orderflow.notificationservice.kafka;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Producers publish JSON without type headers (spring.json.add.type.headers=false), so each
 * topic needs its own consumer factory bound to the concrete event type it carries.
 */
@Configuration
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEvent> orderConfirmedKafkaListenerContainerFactory() {
        return containerFactory(OrderConfirmedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderFailedEvent> orderFailedKafkaListenerContainerFactory() {
        return containerFactory(OrderFailedEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(Class<T> targetType) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties(null);

        JsonDeserializer<T> valueDeserializer = new JsonDeserializer<>(targetType);
        valueDeserializer.addTrustedPackages("*");
        valueDeserializer.setUseTypeMapperForKey(false);
        valueDeserializer.setRemoveTypeHeaders(false);

        DefaultKafkaConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(
                consumerProps, new org.apache.kafka.common.serialization.StringDeserializer(), valueDeserializer);

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
