package com.dauducbach.notification_service.entity;

import com.dauducbach.notification_service.constant.EmailTypeConfig;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("email_config")
public class EmailConfig {
    @Id
    String id;
    String userId;
    EmailTypeConfig emailTypeConfig;
    boolean isEnable    ;
}
