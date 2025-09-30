package com.dauducbach.storage_service.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Table("media")
public class Media {
    @Id
    String assetId;
    String publicId;
    int width;
    int height;
    String format;
    String resourceType;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant createAt;

    int bytes;
    String url;
    String secureUrl;
    String ownerId;
    String version;
    String versionId;
    boolean isAvatar;
}
