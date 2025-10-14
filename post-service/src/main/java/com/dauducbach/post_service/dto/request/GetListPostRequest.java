package com.dauducbach.post_service.dto.request;

import com.dauducbach.post_service.constant.MediaType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class GetListPostRequest {
    List<String> postIds;
    MediaType mediaType;
}
