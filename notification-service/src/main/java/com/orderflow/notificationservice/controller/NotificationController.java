package com.orderflow.notificationservice.controller;

import com.orderflow.notificationservice.dto.NotificationLogResponse;
import com.orderflow.notificationservice.entity.NotificationLog;
import com.orderflow.notificationservice.repository.NotificationLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Unauthenticated demo/debugging endpoint, not routed through the gateway - this service is
 * only reachable inside the docker network in this phase.
 */
@RestController
@RequestMapping("/internal/notifications")
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @GetMapping("/{orderId}")
    public List<NotificationLogResponse> getByOrderId(@PathVariable Long orderId) {
        return notificationLogRepository.findByOrderIdOrderBySentAtDesc(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    private NotificationLogResponse toResponse(NotificationLog notificationLog) {
        return new NotificationLogResponse(notificationLog.getId(), notificationLog.getOrderId(),
                notificationLog.getType(), notificationLog.getChannel(), notificationLog.getRecipient(),
                notificationLog.getContent(), notificationLog.getSentAt());
    }
}
