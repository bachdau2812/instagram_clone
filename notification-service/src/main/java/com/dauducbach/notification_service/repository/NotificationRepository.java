package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.entity.Notification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, String> {
}
