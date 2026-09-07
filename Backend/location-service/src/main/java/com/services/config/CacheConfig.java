package com.services.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig {

    // Picked up automatically by Spring Boot's Redis cache auto-configuration in place of its own
    // default - JSON serialization keeps cached entries inspectable via redis-cli (JDK default
    // serialization would need every DTO to implement Serializable and produce unreadable binary).
    // TTL is a backstop, not the primary staleness guard - see CityService/AirportService for why
    // explicit eviction isn't needed yet.
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }
}
