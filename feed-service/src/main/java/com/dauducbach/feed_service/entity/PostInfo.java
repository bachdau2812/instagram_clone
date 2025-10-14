package com.dauducbach.feed_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class PostInfo {
    String postId;
    String score;
    Long createAt;

    @Builder.Default
    boolean isRead = false;

    @Builder.Default
    boolean isTrending = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostInfo that)) return false;
        return Objects.equals(postId, that.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId);
    }
}
