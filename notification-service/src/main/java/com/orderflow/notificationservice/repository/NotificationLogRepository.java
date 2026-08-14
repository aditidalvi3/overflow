package com.orderflow.notificationservice.repository;

import com.orderflow.notificationservice.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByOrderIdOrderBySentAtDesc(Long orderId);
}
