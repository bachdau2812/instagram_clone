package com.dauducbach.event.profile_operation;

import com.dauducbach.fanout_service.constant.ProfileOperationType;

public record NotificationUserConnectSuccessEvent(ProfileOperationType type, String sourceId, String targetId) {

}
