package com.dauducbach.event.profile_operation;

import com.dauducbach.profile_service.constant.ProfileOperationType;

public record ProfileOperationFailEvent(ProfileOperationType type, String sourceId, String targetId) {
}
