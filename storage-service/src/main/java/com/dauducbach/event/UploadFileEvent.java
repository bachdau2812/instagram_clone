package com.dauducbach.event;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class UploadFileEvent {
    String owner;
    List<FilePart> medias;
    boolean isAvatar;
}
