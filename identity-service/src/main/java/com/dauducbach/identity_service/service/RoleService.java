package com.dauducbach.identity_service.service;

import com.dauducbach.identity_service.dto.request.RoleCreationRequest;
import com.dauducbach.identity_service.entity.Role;
import com.dauducbach.identity_service.exception.ErrorCode;
import com.dauducbach.identity_service.mapper.RoleMapper;
import com.dauducbach.identity_service.repository.RoleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j

public class RoleService {
    RoleRepository roleRepository;
    RoleMapper roleMapper;

    public Mono<Role> createRole(RoleCreationRequest request) {
        var role = roleMapper.toRole(request);
        return roleRepository.save(role);
    }

    public Flux<Role> getAllRole(){
        return roleRepository.findAll();
    }

    public Mono<Void> deleteAllRole() {
        return roleRepository.deleteAll();
    }
}
