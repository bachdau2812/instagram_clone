package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UpdateReadPostFeedEvent {
    String userId;
    List<String> postIds;
}
