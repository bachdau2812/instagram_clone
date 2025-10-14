package com.dauducbach.feed_service.repository;


import com.dauducbach.feed_service.entity.UserFeed;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface UserFeedRepository extends ReactiveElasticsearchRepository<UserFeed, String> {
}
