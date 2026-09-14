package com.amrproject.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * 18단계: MQTT/VDA 5050 로봇 통신 어댑터(옵트인, 기본 비활성 — docs/API.md §7).
 * app.robot-gateway.mqtt.enabled=true일 때만 브로커에 연결한다.
 *
 * <p>44단계: 브로커 계정(username/password)과 TLS를 지원한다. broker-url이 {@code ssl://}이면 TLS로 접속하고,
 * {@code tls-ca-file}(PEM)을 주면 그 인증 기관만 신뢰한다(자체 서명·사내 CA). 주지 않으면 JVM 기본 신뢰 저장소를 쓴다.
 */
@Configuration
@ConditionalOnProperty(name = "app.robot-gateway.mqtt.enabled", havingValue = "true")
public class MqttConfig {

    @Value("${app.robot-gateway.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${app.robot-gateway.mqtt.username:}")
    private String username;

    @Value("${app.robot-gateway.mqtt.password:}")
    private String password;

    @Value("${app.robot-gateway.mqtt.tls-ca-file:}")
    private String tlsCaFile;

    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        if (!tlsCaFile.isBlank()) {
            options.setSocketFactory(socketFactoryTrusting(Path.of(tlsCaFile)));
        }
        client.connect(options);
        return client;
    }

    /** PEM 인증서(여러 개 가능)만 신뢰하는 TLS 소켓 팩토리. 서버 인증서의 호스트 이름 검증은 Paho 기본값(켜짐)을 따른다. */
    static SSLSocketFactory socketFactoryTrusting(Path caFile) {
        try (InputStream in = Files.newInputStream(caFile)) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                trustStore.setCertificateEntry("mqtt-ca-" + index++, certificate);
            }
            if (index == 0) {
                throw new IllegalStateException("MQTT TLS CA 파일에 인증서가 없습니다: " + caFile);
            }
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("MQTT TLS CA 파일을 읽을 수 없습니다: " + caFile, e);
        }
    }
}
