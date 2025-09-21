package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.entity.NotificationDB;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface NotificationRepository extends ReactiveCrudRepository<NotificationDB, String> {
}
