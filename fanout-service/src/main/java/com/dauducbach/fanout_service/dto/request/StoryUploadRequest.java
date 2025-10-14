package com.dauducbach.fanout_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class StoryUploadRequest {
    String userId;
    FilePart media;
    String audioDisplayName;
}
