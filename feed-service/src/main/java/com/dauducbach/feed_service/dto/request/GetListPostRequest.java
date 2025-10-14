package com.dauducbach.feed_service.dto.request;

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