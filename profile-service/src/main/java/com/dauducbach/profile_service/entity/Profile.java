package com.dauducbach.profile_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

@Table("profiles")
public class Profile {
    @Id
    String id;
    String username;
    String displayName;
    String phoneNumber;
    String email;
    String city;
    String job;

    @Builder.Default
    int followerCount = 0;

    @Builder.Default
    int followingCount = 0;

    @Builder.Default
    boolean isPublic = true;

    String sex;
    String bio;
}
