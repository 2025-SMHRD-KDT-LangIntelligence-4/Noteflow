package com.smhrd.web.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.username}")
    private String username;

    @Value("${spring.data.mongodb.password}")
    private String password;

    @Value("${spring.data.mongodb.host}")
    private String host;

    @Value("${spring.data.mongodb.port}")
    private int port;

    @Value("${spring.data.mongodb.authentication-database}")
    private String authDb;

    @Value("${spring.data.mongodb.database}")
    private String database;

    // 몽
    @Bean
    public MongoClient mongoClient() {
        String uri = String.format(
                "mongodb://%s:%s@%s:%d/?authSource=%s",
                username, password, host, port, authDb
        );
        return MongoClients.create(uri);
    }


    @Bean
    public GridFSBucket gridFSBucket(MongoClient mongoClient) {
        return GridFSBuckets.create(mongoClient.getDatabase(database));
    }
}
