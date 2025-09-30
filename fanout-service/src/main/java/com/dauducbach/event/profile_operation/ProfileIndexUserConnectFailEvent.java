package com.dauducbach.event.profile_operation;

import com.dauducbach.fanout_service.constant.ProfileOperationType;

public record ProfileIndexUserConnectFailEvent(ProfileOperationType type, String sourceId, String targetId) {
}
