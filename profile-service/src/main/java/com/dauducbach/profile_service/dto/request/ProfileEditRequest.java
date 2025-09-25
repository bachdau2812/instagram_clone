package com.dauducbach.profile_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class ProfileEditRequest {
    String profileId;
    String fieldName;
    String value;
}
