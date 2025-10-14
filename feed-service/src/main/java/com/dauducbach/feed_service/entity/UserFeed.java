package com.dauducbach.feed_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

@Document(indexName = "user_feed")
public class UserFeed {
    @Id
    String userId;
    Set<PostInfo> feedList;
}
