package com.orderflow.notificationservice.dto;

import com.orderflow.notificationservice.entity.NotificationType;

import java.time.Instant;

public record NotificationLogResponse(Long id, Long orderId, NotificationType type, String channel,
                                       String recipient, String content, Instant sentAt) {
}
