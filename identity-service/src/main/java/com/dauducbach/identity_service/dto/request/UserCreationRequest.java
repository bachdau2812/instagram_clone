package com.dauducbach.identity_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UserCreationRequest {
    String username;
    String password;
    String displayName;
    String phoneNumber;
    String email;
    String city;
    String job;
    String sex;
    String dob;

    @Builder.Default
    String role = "USER";
}