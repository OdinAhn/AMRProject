# 35단계: 백엔드 이미지. 빌드 단계에서 bootJar만 만들고(테스트는 CI가 돌린다), 실행 단계는 JRE + 비루트 사용자.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 의존성 층을 소스와 분리해 소스만 바뀌면 다시 받지 않는다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar')" app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system amr && useradd --system --gid amr --home-dir /app amr
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER amr
# 8080: API, 8081: actuator(관리 포트, 외부 비공개)
EXPOSE 8080 8081
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
