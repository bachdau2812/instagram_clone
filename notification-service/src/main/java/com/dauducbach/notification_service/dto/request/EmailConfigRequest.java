package com.dauducbach.notification_service.dto.request;

import com.dauducbach.notification_service.constant.EmailTypeConfig;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class EmailConfigRequest {
    String userId;
    EmailTypeConfig emailTypeConfig;
    boolean isEnable;
}
