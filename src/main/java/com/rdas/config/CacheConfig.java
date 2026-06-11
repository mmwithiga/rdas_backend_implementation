package com.rdas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig {

    @Value("${cache.ttl.countries:86400}")
    private long countriesTtlSeconds;

    @Value("${cache.ttl.currencies:86400}")
    private long currenciesTtlSeconds;

    @Value("${cache.ttl.continents:604800}")
    private long continentsTtlSeconds;

    @Value("${cache.ttl.languages:86400}")
    private long languagesTtlSeconds;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                CacheNames.COUNTRIES_ALL,      defaults.entryTtl(Duration.ofSeconds(countriesTtlSeconds)),
                CacheNames.COUNTRY_BY_CODE,    defaults.entryTtl(Duration.ofSeconds(countriesTtlSeconds)),
                CacheNames.CURRENCIES_ALL,     defaults.entryTtl(Duration.ofSeconds(currenciesTtlSeconds)),
                CacheNames.CURRENCY_COUNTRIES, defaults.entryTtl(Duration.ofSeconds(currenciesTtlSeconds)),
                CacheNames.CONTINENTS_ALL,     defaults.entryTtl(Duration.ofSeconds(continentsTtlSeconds)),
                CacheNames.LANGUAGES_ALL,      defaults.entryTtl(Duration.ofSeconds(languagesTtlSeconds))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    public static final class CacheNames {
        public static final String COUNTRIES_ALL      = "countries:all";
        public static final String COUNTRY_BY_CODE    = "countries:byCode";
        public static final String CURRENCIES_ALL     = "currencies:all";
        public static final String CURRENCY_COUNTRIES = "currencies:countries";
        public static final String CONTINENTS_ALL     = "continents:all";
        public static final String LANGUAGES_ALL      = "languages:all";

        private CacheNames() {}
    }
}
