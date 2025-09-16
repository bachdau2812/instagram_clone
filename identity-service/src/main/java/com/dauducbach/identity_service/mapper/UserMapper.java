package com.dauducbach.identity_service.mapper;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.identity_service.dto.request.UserCreationRequest;
import com.dauducbach.identity_service.dto.response.UserResponse;
import com.dauducbach.identity_service.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toUserResponse(User user);
    ProfileCreationEvent toProfileCreationEvent(UserCreationRequest request);
}
