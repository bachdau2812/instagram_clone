package com.dauducbach.profile_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class GetListFollowerResponse {
    String userId;
    List<FollowItemResponse> followerList;
}
