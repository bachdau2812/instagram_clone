package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.entity.UserNotificationConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface NotificationConfigRepository extends ReactiveCrudRepository<UserNotificationConfig, String> {
}
