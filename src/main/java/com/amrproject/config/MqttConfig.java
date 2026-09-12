package com.amrproject.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 18단계: MQTT/VDA 5050 로봇 통신 어댑터(옵트인, 기본 비활성 — docs/API.md §7).
 * app.robot-gateway.mqtt.enabled=true일 때만 브로커에 연결한다.
 */
@Configuration
@ConditionalOnProperty(name = "app.robot-gateway.mqtt.enabled", havingValue = "true")
public class MqttConfig {

    @Value("${app.robot-gateway.mqtt.broker-url}")
    private String brokerUrl;

    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        client.connect(options);
        return client;
    }
}
