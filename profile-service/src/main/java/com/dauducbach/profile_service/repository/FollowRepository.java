package com.dauducbach.profile_service.repository;

import com.dauducbach.profile_service.entity.Follow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface FollowRepository extends ReactiveCrudRepository<Follow, String> {

}
