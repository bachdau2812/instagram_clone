package com.dauducbach.notification_service.repository;

import com.dauducbach.notification_service.entity.NotificationTemplate;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface NotificationTemplateRepository extends ReactiveCrudRepository<NotificationTemplate, String> {
}
