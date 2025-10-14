package com.dauducbach.post_service.dto.request;

import com.dauducbach.post_service.constant.MediaType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class GetFileRequest {
    String ownerId;
    MediaType mediaType;
}
