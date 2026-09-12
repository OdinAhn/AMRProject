package com.amrproject.service;

import com.amrproject.config.KafkaConfig;
import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 16단계: OutboxPublisher가 실제 Kafka 브로커(Testcontainers)에도 이벤트를
 * 발행하는지 검증한다 — Redis와 달리 실제 브로커를 구독해 라이브 확인이 가능하다.
 */
class OutboxKafkaPublishTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    void PENDING_이벤트가_Kafka_토픽에도_실제로_도착한다() {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(KafkaConfig.EVENT_TOPIC));
            consumer.poll(Duration.ofMillis(500));

            OutboxEvent event = outboxEventRepository.save(new OutboxEvent("TASK", 999L, "task.kafka_test", null));
            outboxPublisher.publishPending();

            ConsumerRecord<String, String> record = pollUntilFound(consumer, event.getId());

            assertThat(record.key()).isEqualTo(String.valueOf(event.getId()));
            assertThat(record.value()).contains("\"eventType\":\"task.kafka_test\"");
            assertThat(record.value()).contains("\"aggregateId\":999");
        }
    }

    private ConsumerRecord<String, String> pollUntilFound(KafkaConsumer<String, String> consumer, Long eventId) {
        for (int i = 0; i < 10; i++) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                if (record.key().equals(String.valueOf(eventId))) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka 토픽에서 eventId=" + eventId + " 메시지를 찾지 못했습니다.");
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-kafka-publish-test-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }
}
