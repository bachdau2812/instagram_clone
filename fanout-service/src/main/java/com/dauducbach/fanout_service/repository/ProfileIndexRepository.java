package com.dauducbach.fanout_service.repository;

import com.dauducbach.fanout_service.entity.ProfileIndex;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface ProfileIndexRepository extends ReactiveElasticsearchRepository<ProfileIndex, String> {

}
