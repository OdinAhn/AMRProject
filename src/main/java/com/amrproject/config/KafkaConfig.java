package com.amrproject.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

/**
 * 16단계: Kafka 이벤트 로그 (docs/API.md §5). Redis는 실시간 팬아웃(SSE),
 * Kafka는 나중에 다른 시스템이 재구독할 수 있는 내구성 있는 이벤트 로그 역할이다.
 *
 * <p>이 Spring Boot 버전의 spring-kafka는 자동 구성(autoconfigure) 모듈을 별도로
 * 제공하지 않아(spring-boot-autoconfigure/spring-kafka 어디에도 Kafka
 * AutoConfiguration이 없음) ProducerFactory/KafkaTemplate/KafkaAdmin을 직접 빈으로 등록한다.
 */
@Configuration
public class KafkaConfig {

    public static final String EVENT_TOPIC = "amr.events";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configs = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    @Bean
    public NewTopic amrEventsTopic() {
        return TopicBuilder.name(EVENT_TOPIC).partitions(1).replicas(1).build();
    }
}
