package com.dauducbach.chat_service.dto.request;

import lombok.Data;

@Data

public class ClientPing {
    String type;
    String userId;
}
