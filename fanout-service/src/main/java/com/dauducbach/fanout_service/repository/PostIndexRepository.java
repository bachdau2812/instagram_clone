package com.dauducbach.fanout_service.repository;

import com.dauducbach.fanout_service.entity.PostIndex;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface PostIndexRepository extends ReactiveElasticsearchRepository<PostIndex, String> {
}
