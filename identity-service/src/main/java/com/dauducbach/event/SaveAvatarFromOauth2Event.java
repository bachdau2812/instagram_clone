package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveAvatarFromOauth2Event {
    String ownerId;
    String avatarUrl;
    boolean isAvt;
}
