package com.dauducbach.profile_service.repository;

import com.dauducbach.profile_service.entity.Profile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ProfileRepository extends ReactiveCrudRepository<Profile, String> {

}
