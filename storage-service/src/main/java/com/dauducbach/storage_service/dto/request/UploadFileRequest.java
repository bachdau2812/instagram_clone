package com.dauducbach.storage_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UploadFileRequest {
    String owner;
    List<FilePart> medias;
    boolean isAvatar;
}
