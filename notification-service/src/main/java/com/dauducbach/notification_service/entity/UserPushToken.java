package com.dauducbach.notification_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("push_token")
public class UserPushToken {
    @Id
    String id;
    String userId;
    String deviceId;
    String deviceToken;
    Instant createAt;
}
