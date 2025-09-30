package com.dauducbach.event.profile_operation;


import com.dauducbach.fanout_service.constant.ProfileOperationType;

public record ProfileOperationSuccessEvent(ProfileOperationType type, String sourceId, String targetId) {
}
