package com.dauducbach.event.profile_operation;

import com.dauducbach.fanout_service.constant.ProfileOperationType;

public record ProfileIndexUserConnectSuccessEvent(ProfileOperationType type, String sourceId, String targetId) {
}
