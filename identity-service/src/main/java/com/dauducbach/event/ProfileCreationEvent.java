package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ProfileCreationEvent {
    String userId;
    String username;
    String displayName;
    String phoneNumber;
    String email;
    String city;
    String job;

    LocalDate dob;
}
