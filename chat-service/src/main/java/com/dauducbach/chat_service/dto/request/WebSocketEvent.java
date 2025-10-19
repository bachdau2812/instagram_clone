package com.dauducbach.chat_service.dto.request;

import lombok.Data;

@Data
public abstract class WebSocketEvent {
    private String type;
}
