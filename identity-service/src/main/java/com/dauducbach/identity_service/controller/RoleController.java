package com.dauducbach.identity_service.controller;

import com.dauducbach.identity_service.dto.request.RoleCreationRequest;
import com.dauducbach.identity_service.entity.Role;
import com.dauducbach.identity_service.service.RoleService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@RequestMapping("/roles")
public class RoleController {
    RoleService roleService;

    @PostMapping("/create")
    Mono<Role> createRole(@RequestBody RoleCreationRequest request) {
        return roleService.createRole(request);
    }

    @GetMapping("/get-all")
    Flux<Role> getAllRole() {
        return roleService.getAllRole();
    }

    @GetMapping("/delete-all")
    Mono<Void> deleteAllRole() {
        return roleService.deleteAllRole();
    }
}
