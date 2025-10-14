package com.dauducbach.post_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("story_item")
public class StoryItem {
    @Id
    String id;
    String mediaUrl;
    String audioUrl;    // null neu media la video
    Instant createAt;
    boolean isExpired;

    String storyId;
}
