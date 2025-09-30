package com.dauducbach.fanout_service.controller;

import com.dauducbach.fanout_service.dto.request.ProfileOperationRequest;
import com.dauducbach.fanout_service.service.FanoutForProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

public class FanoutController {
    FanoutForProfileService fanoutForProfileService;

    @PostMapping("/follow")
    Mono<Void> followOperator(@RequestBody ProfileOperationRequest request) {
        return fanoutForProfileService.profileOperationHandle(request);
    }
}
