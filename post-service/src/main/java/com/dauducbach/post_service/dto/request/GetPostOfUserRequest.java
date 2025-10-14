package com.dauducbach.post_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class GetPostOfUserRequest {
    String userId;
    int currentIndex;
    int limit;
}
