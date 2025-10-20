package com.smhrd.web.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.concurrent.TimeUnit;

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

    // ✅ 애플리케이션 완전 시작 후 실행
    @EventListener(ApplicationReadyEvent.class)
    public void setupTTLIndex(ApplicationReadyEvent event) {
        try {
            // ✅ 이벤트에서 ApplicationContext 가져오기
            MongoClient mongoClient = event.getApplicationContext()
                    .getBean(MongoClient.class);

            MongoCollection<Document> filesCollection =
                    mongoClient.getDatabase(database)
                            .getCollection("fs.files");

            IndexOptions options = new IndexOptions()
                    .expireAfter(30L, TimeUnit.DAYS);

            filesCollection.createIndex(
                    Indexes.ascending("uploadDate"),
                    options
            );

            System.out.println("✅ TTL 인덱스 생성 완료: 30일 후 자동 삭제");

        } catch (Exception e) {
            System.err.println("⚠️ TTL 인덱스 생성 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
