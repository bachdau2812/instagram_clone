package com.dauducbach.event.post_creation;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class SavePostIndexCommand {
    String postId;
    String userId;
    String content;
    List<String> tags;
    Long createAt;
    Long updateAt;
}
