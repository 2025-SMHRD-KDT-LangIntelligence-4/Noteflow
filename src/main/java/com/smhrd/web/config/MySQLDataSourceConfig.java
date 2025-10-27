package com.smhrd.web.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * MySQL DataSource + JdbcTemplate 설정
 *
 * 목적:
 * 1. MySQL DataSource 정의
 * 2. JdbcTemplate Bean 등록 (강의/태그 쿼리용)
 * 3. NamedParameterJdbcTemplate Bean 등록 (:paramName 형식 쿼리용)
 */
@Configuration
public class MySQLDataSourceConfig {

    /**
     * ✅ MySQL DataSource Bean
     * - spring.datasource.* 설정에서 자동으로 읽음
     * - HikariCP 커넥션 풀 사용
     */
    @Bean
    @Primary
    public DataSource mysqlDataSource(DataSourceProperties properties) {
        // DataSourceProperties가 application.properties에서 spring.datasource.* 읽음
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * ✅ JdbcTemplate Bean
     * - SQL 쿼리 실행용
     * - 예: jdbcTemplate.queryForList("SELECT ...", params)
     */
    @Bean
    @Primary
    public JdbcTemplate mysqlJdbcTemplate(DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    /**
     * ✅ NamedParameterJdbcTemplate Bean
     * - :paramName 형식의 파라미터 지원
     * - 예: :userId, :tagNames 등
     * - LectureRecommendService에서 사용
     */
    @Bean(name = "mysqlNamedParameterJdbcTemplate")
    @Primary
    public NamedParameterJdbcTemplate mysqlNamedParameterJdbcTemplate(DataSource mysqlDataSource) {
        return new NamedParameterJdbcTemplate(mysqlDataSource);
    }
}