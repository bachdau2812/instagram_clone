package com.dauducbach.event.user_service;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ForgetPasswordEvent {
    String email;
    String code;
}
