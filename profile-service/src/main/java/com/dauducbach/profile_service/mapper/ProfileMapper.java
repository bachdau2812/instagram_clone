package com.dauducbach.profile_service.mapper;

import com.dauducbach.event.ProfileCreationEvent;
import com.dauducbach.profile_service.entity.Profile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    Profile toProfile(ProfileCreationEvent event);
}
