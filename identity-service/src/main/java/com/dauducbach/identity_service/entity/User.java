package com.dauducbach.identity_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

@Table(name = "users")
public class User {
    @Id
    String id;
    String username;
    String password;
    String email;
    String provider;
    String providerId;
}
