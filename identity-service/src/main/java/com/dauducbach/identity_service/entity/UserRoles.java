package com.dauducbach.identity_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

@Table(name = "user_roles")
public class UserRoles {
    String userId;
    String roleName;
}
