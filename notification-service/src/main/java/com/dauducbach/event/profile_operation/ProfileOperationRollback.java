package com.dauducbach.event.profile_operation;


import com.dauducbach.notification_service.constant.ProfileOperationType;

public record ProfileOperationRollback(
        ProfileOperationType type, String sourceId, String targetId
) {
}
