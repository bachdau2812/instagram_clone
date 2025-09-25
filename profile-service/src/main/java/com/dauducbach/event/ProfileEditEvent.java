package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class ProfileEditEvent {
    String profileId;
    String fieldName;
    String value;
}
