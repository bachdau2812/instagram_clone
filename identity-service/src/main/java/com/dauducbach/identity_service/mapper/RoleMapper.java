package com.dauducbach.identity_service.mapper;

import com.dauducbach.identity_service.dto.request.RoleCreationRequest;
import com.dauducbach.identity_service.entity.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    Role toRole(RoleCreationRequest request);
}
