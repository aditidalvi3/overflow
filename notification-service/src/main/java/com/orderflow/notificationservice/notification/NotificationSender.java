package com.orderflow.notificationservice.notification;

/**
 * Seam for delivering a notification through whatever channel a later phase plugs in
 * (real email/SMS provider). Kafka consumers depend only on this interface, so swapping
 * the implementation never touches consumer/service code.
 */
public interface NotificationSender {

    void send(String recipientPlaceholder, String subject, String body);
}
