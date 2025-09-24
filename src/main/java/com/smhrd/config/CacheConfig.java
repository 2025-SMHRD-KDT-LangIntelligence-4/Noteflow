package com.smhrd.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching   // [추가] Spring Cache 활성화
public class CacheConfig {
    // 캐시 관련 설정을 확장하려면 여기에 작성
    // 기본은 메모리 캐싱 (ConcurrentMapCacheManager 사용)
}