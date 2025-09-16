package com.dauducbach.identity_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class ErrorResponse {
    int status;
    String message;
    List<String> details;
    String path;
    Instant timestamp;

    public ErrorResponse(int status, String message, List<String> details) {
        this.status = status;
        this.message = message;
        this.details = details;
        this.timestamp = Instant.now();
    }
}
