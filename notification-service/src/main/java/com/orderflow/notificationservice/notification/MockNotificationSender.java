package com.orderflow.notificationservice.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orderflow.notification", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

    @Override
    public void send(String recipientPlaceholder, String subject, String body) {
        log.info("[MOCK EMAIL] to={} subject={} body={}", recipientPlaceholder, subject, body);
    }
}
