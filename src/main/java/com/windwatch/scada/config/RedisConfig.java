package com.windwatch.scada.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 캐시 설정
 * Redis가 없으면 ConcurrentMap 기반 인메모리 캐시로 자동 폴백
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000ms}")
    private java.time.Duration timeout;

    /**
     * Redis 연결 팩토리 — 연결 실패 시 로그만 출력하고 애플리케이션 계속 구동
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setValidateConnection(false); // 시작 시 연결 강제 검증 안 함
        return factory;
    }

    /**
     * Redis CacheManager — Redis 사용 불가 시 폴백 매니저로 대체
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // Redis 연결 가능 여부 테스트
            connectionFactory.getConnection().ping();

            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JavaTimeModule());
            om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(om);

            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(10))
                    .serializeKeysWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                    .disableCachingNullValues();

            log.info("Redis 캐시 매니저 활성화 ({}:{})", redisHost, redisPort);
            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(config)
                    .withCacheConfiguration("dashboardSummary",
                            config.entryTtl(Duration.ofSeconds(10)))
                    .withCacheConfiguration("turbineIds",
                            config.entryTtl(Duration.ofSeconds(10)))
                    .withCacheConfiguration("weatherData",
                            config.entryTtl(Duration.ofMinutes(5)))
                    .build();

        } catch (Exception e) {
            log.warn("Redis 연결 불가 — 인메모리 캐시로 폴백합니다. ({})", e.getMessage());
            return new ConcurrentMapCacheManager("dashboardSummary", "turbineIds", "weatherData");
        }
    }

    /**
     * RedisTemplate (직접 Redis 조작이 필요한 경우)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
