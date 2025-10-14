package com.dauducbach.feed_service.configuration;

import com.dauducbach.feed_service.dto.response.PostResponse;
import com.dauducbach.feed_service.dto.response.StoryInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext(stringRedisSerializer)
                .key(stringRedisSerializer)
                .value(valueSerializer)
                .hashKey(stringRedisSerializer)
                .hashValue(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(reactiveRedisConnectionFactory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, StoryInfo> redisStoryInfo(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<StoryInfo> serializer = new Jackson2JsonRedisSerializer<>(StoryInfo.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, StoryInfo> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, StoryInfo> context = builder
                .value(serializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, PostResponse> redisPostResponse(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<PostResponse> serializer = new Jackson2JsonRedisSerializer<>(PostResponse.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, PostResponse> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, PostResponse> context = builder
                .value(serializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
