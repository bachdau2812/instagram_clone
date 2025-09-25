package com.dauducbach.event;

import com.dauducbach.fanout_service.constant.ProfileOperationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class ProfileOperationEvent {
    ProfileOperationType type;
    String sourceId;
    String targetId;
}
