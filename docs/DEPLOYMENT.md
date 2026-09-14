# 배포 가이드 (35단계)

이 문서는 전체 스택을 컨테이너로 띄우는 **단일 호스트 기준 운영형 구성**을 설명한다. Kubernetes 매니페스트, TLS 종단, 백업 자동화는 아직 없다(아래 "남은 과제").

## 1. 구성

```
브라우저 ──80──▶ frontend (nginx)
                   ├─ 정적 파일(React 빌드)
                   └─ /api/** ──▶ backend:8080 (Spring Boot, prod 프로필)
                                    ├─ mysql:3306
                                    ├─ redis:6379   (SSE 팬아웃)
                                    └─ kafka:9092   (이벤트 로그)
Prometheus ─────────────────▶ backend:8081 (actuator 관리 포트, 외부 비공개)
```

| 파일 | 역할 |
|---|---|
| `Dockerfile` | 백엔드 이미지. Gradle로 `bootJar`만 만들고(테스트는 CI 담당) JRE 이미지에서 비루트 사용자로 실행. 컨테이너 헬스체크는 관리 포트의 `/actuator/health/readiness` |
| `frontend/Dockerfile` | 프론트 이미지. `npm run build` 결과를 nginx로 서빙 |
| `frontend/nginx.conf.template` | `/api` 프록시(백엔드 주소는 `BACKEND_URL`), SSE 버퍼링 끄기, SPA 경로 대체, 빌드 산출물 캐시 |
| `docker-compose.prod.yml` | MySQL·Redis·Kafka·backend·frontend 전체 스택. 인프라 포트는 호스트에 열지 않고 화면 포트(`HTTP_PORT`)만 연다 |
| `.env.example` | compose가 받는 환경 변수 목록. 복사해 `.env`로 쓴다(`.gitignore`에 포함) |
| `src/main/resources/application-prod.yaml` | 운영 프로필. 모든 접속 정보·비밀값을 환경 변수로만 받는다 |

개발용 `docker-compose.yml`(인프라만, 포트 공개)은 그대로 두었다. `docker-compose.prod.yml`은 compose 프로젝트 이름을 `amr-prod`로 따로 둬서 둘의 컨테이너·볼륨이 섞이지 않는다.

## 2. 처음 띄우기

```bash
cp .env.example .env
# .env 편집: DB_PASSWORD, MYSQL_ROOT_PASSWORD, TOKEN_SIGNING_SECRET, EXTERNAL_API_KEY,
#            BOOTSTRAP_ADMIN_USERNAME/PASSWORD(빈 DB 첫 기동용)
#   비밀값 생성 예: openssl rand -base64 48

docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps        # backend가 (healthy)가 될 때까지 기다린다
```

- 빈 데이터베이스면 Flyway가 기동 시 전체 마이그레이션을 적용하고, `BOOTSTRAP_ADMIN_*`로 최초 관리자를 만든다. 관리자가 생긴 뒤에는 두 값을 `.env`에서 지우고 재기동하는 것을 권장한다(남아 있어도 관리자가 있으면 아무 일도 하지 않는다).
- 화면은 `http://<호스트>:${HTTP_PORT}`(기본 80)에서 열린다.
- 필수 값(`TOKEN_SIGNING_SECRET`, DB 계정)이 비어 있으면 compose가 시작 전에 오류를 낸다. `EXTERNAL_API_KEY`가 비어 있으면 외부 API(`/api/external/**`)는 전부 401이다(fail-closed).

## 3. prod 프로필이 local과 다른 점

| 항목 | local | prod |
|---|---|---|
| 비밀값 기본값 | 개발용 기본값 있음 | 없음 — 토큰 서명 키가 없으면 기동 실패 |
| SQL 로그 | 출력 | 끔 |
| 자동 디스패치·연결 감시 | 켜짐 | 켜짐(환경 변수로 끌 수 있음) |
| actuator | 앱 포트, `health`만 | **관리 포트 8081**, `health`·`info`·`prometheus` |
| 종료 | 즉시 | graceful — 새 요청을 막고 진행 중 요청을 최대 20초 기다림 |
| 프록시 헤더 | - | `X-Forwarded-*` 신뢰(`server.forward-headers-strategy: framework`) |

**클라이언트 IP(36단계)**: 로그인 IP 제한은 백엔드가 받은 `X-Forwarded-For`를 클라이언트 주소로 쓴다. 컨테이너 nginx는 이 헤더를 자기에게 접속한 주소(`$remote_addr`)로 덮어쓴다 — 클라이언트가 보낸 값을 이어 붙이면 조작한 첫 값이 쓰여 제한을 우회할 수 있기 때문이다. nginx 앞에 로드밸런서를 또 두면 모든 요청이 LB 주소 하나로 보여 IP 제한이 전체에 걸리므로, nginx에 `set_real_ip_from <LB 대역>; real_ip_header X-Forwarded-For;`를 추가해야 한다.

DB 접속 URL의 SSL 옵션은 `DB_URL_OPTIONS`(기본 `useSSL=false&allowPublicKeyRetrieval=true`)로 바꾼다. 관리형 MySQL을 쓰면 `sslMode=REQUIRED` 등으로 교체한다.

## 4. 헬스체크 · 메트릭

- **헬스**: 관리 포트의 `/actuator/health/liveness`, `/actuator/health/readiness`(Kubernetes 프로브용). 앱 포트의 `/actuator/health`도 인증 없이 열려 있다(상세 정보는 숨김).
- **메트릭**: 관리 포트의 `/actuator/prometheus`. 관리 포트로 들어온 요청은 토큰 없이 허용하고, 앱 포트(8080)로 들어온 나머지 actuator 요청은 `ADMIN` 토큰이 필요하다. nginx는 `/actuator/**`를 404로 막는다. **관리 포트를 외부에 공개하지 않는 것**이 보안 전제다.
- 기본 메트릭(JVM, HTTP 요청, HikariCP 커넥션 풀, Kafka 프로듀서 등) 외에 업무 게이지를 노출한다(`OperationalMetrics`).

| 메트릭 | 의미 | 경보 예시 |
|---|---|---|
| `amr_outbox_events_pending` | 발행 대기 아웃박스 이벤트 수 | 5분 넘게 증가 → Kafka/Redis 발행이 막힘, 실시간 화면 지연 |
| `amr_tasks{status="QUEUED\|RUNNING\|PAUSED\|RECOVERY_REQUIRED"}` | 상태별 작업 수 | `RECOVERY_REQUIRED` > 0이 오래 지속 → 사람 개입 필요 |
| `amr_alarms_open` | 미확인 알람 수 | > 0 |

Prometheus 수집 설정 예(같은 Docker 네트워크):

```yaml
scrape_configs:
  - job_name: amr-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["backend:8081"]
```

## 4-1. MQTT 브로커 보안 (44단계)

로봇 통신용 브로커(`mosquitto`, eclipse-mosquitto 2)가 운영 compose에 포함된다. 설정은 컨테이너 기동 시 `infra/mosquitto/amr-entrypoint.sh`가 환경 변수·파일로 만든다.

| 항목 | 내용 |
|---|---|
| 익명 접속 | 금지(`allow_anonymous false`) |
| 관제 서버 계정 | `.env`의 `MQTT_USERNAME`/`MQTT_PASSWORD` — manufacturer 아래 모든 로봇 토픽 읽기·쓰기. backend도 같은 값으로 접속 |
| 로봇 계정 | `infra/mosquitto/robot-credentials`(예시 `robot-credentials.example`, 커밋 금지) 한 줄에 `시리얼번호:비밀번호`. 계정 이름은 VDA 5050 `serialNumber` = 서버에 등록한 로봇 코드 |
| 토픽 권한(ACL) | 로봇은 **자기 시리얼번호 토픽만**: `order`·`instantActions` 읽기, `state`·`connection`·`visualization`·`factsheet` 쓰기. 다른 로봇 주문을 엿보거나 다른 로봇 이름으로 상태를 보낼 수 없다 |
| 리스너 | `1883` 평문 — 같은 도커 네트워크의 backend 전용, 호스트에 열지 않음. `8883` TLS — 로봇 접속용(호스트 포트 `MQTT_TLS_PORT`), `infra/mosquitto/certs/server.crt`·`server.key`가 있을 때만 열림 |
| 비밀번호 저장 | 기동할 때마다 해시 파일로 새로 만든다(컨테이너 내부 0600). 로봇 계정을 바꾸면 `docker compose -f docker-compose.prod.yml restart mosquitto` |

인증서:
- 시험·사내망: `sh infra/mosquitto/gen-dev-certs.sh <로봇이 접속할 호스트 이름 또는 IP>` → `infra/mosquitto/certs/`에 `ca.crt`(로봇·서버가 신뢰), `server.crt`, `server.key`(커밋 금지). SAN에 로봇이 쓰는 주소가 들어가야 호스트 이름 검증을 통과한다.
- 운영: 조직 CA나 공인 인증서를 같은 이름으로 둔다.

backend를 브로커 밖(다른 호스트)에서 TLS로 붙일 때는 `MQTT_BROKER_URL=ssl://<호스트>:8883`과 `MQTT_TLS_CA_FILE=<ca.crt 경로>`를 준다(비우면 JVM 기본 신뢰 저장소). 개발용 `docker-compose.yml`의 브로커(mosquitto 1.6)는 여전히 **인증 없는 로컬 개발 전용**이다 — 실제 로봇은 운영 구성에 붙인다.

## 5. 업데이트 · 종료

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build   # 이미지 재빌드 후 교체
docker compose -f docker-compose.prod.yml logs -f backend
```

- 새 버전의 Flyway 마이그레이션은 백엔드 기동 시 자동 적용된다. 되돌리는 마이그레이션은 없으므로 **업데이트 전에 DB를 백업**한다.
- 종료 시 백엔드는 graceful shutdown(최대 20초)을 한다. compose는 `stop_grace_period: 30s`로 그보다 길게 기다린다. 열린 SSE 연결은 이 대기 시간을 다 쓸 수 있고, 끊긴 브라우저는 자동 재접속한다.
- 백엔드는 인스턴스를 여러 개 띄워도 아웃박스 릴레이(`SKIP LOCKED`)·배정 잠금(31단계)이 안전하다. 다만 이 compose는 단일 인스턴스 기준이다.

## 6. 백업 (수동)

```bash
# 백업
docker compose -f docker-compose.prod.yml exec mysql \
  sh -c 'exec mysqldump --single-transaction -u root -p"$MYSQL_ROOT_PASSWORD" amr_project' > amr_$(date +%F).sql
# 복원(빈 DB에)
docker compose -f docker-compose.prod.yml exec -T mysql \
  sh -c 'exec mysql -u root -p"$MYSQL_ROOT_PASSWORD" amr_project' < amr_YYYY-MM-DD.sql
```

Kafka 토픽(`amr.events`)은 이벤트 로그 사본이라 백업 대상이 아니다. 원본은 MySQL의 `outbox_events`·이력 테이블이다. Redis는 휘발 데이터만 담는다.

## 7. 검증 기록 (35단계)

로컬 Docker(29.7)에서 `docker compose -f docker-compose.prod.yml up -d --build`로 확인했다.

- backend 컨테이너 헬스체크가 `healthy`가 되고, 빈 DB에 마이그레이션과 최초 관리자가 생성된다.
- nginx(호스트 포트) 경유: 로그인, 관리자 API 200, 외부 API 키 없이 401, SPA 경로(`/statistics`, `/tasks/1`) 200, `/actuator/prometheus` 404, `index.html`은 `no-cache`.
- 물류센터·작업장·로봇을 만들고 작업을 호출하면 자동 디스패치로 배정되고, 보내기·하역 완료로 `SUCCEEDED`가 된다. 같은 시간 동안 nginx 경유 SSE로 `task.created`~`task.succeeded`, `robot.state_changed` 이벤트를 받는다. Kafka 토픽에 이벤트가 쌓이고 `amr_outbox_events_pending`은 0이다.
- 관리 포트(`backend:8081`)의 readiness `UP`과 업무 게이지 노출, 앱 포트(`backend:8080`)의 prometheus는 토큰 없이 401.
- `docker stop` 시 graceful shutdown 로그가 남고 정상 종료된다.

## 8. 남은 과제

- TLS: 앞단 리버스 프록시(또는 로드밸런서)에서 종단한다고 가정한다. nginx 이미지에는 인증서 설정이 없다.
- Kubernetes 매니페스트·Helm 차트, 비밀값 관리(Vault/Secrets Manager).
- Grafana 대시보드·경보 규칙 파일, 로그 수집(현재는 컨테이너 표준 출력).
- 자동 백업·복원 리허설, MySQL/Kafka/Redis 이중화.
- ~~MQTT 브로커가 compose에 없음~~ → 44단계에서 인증·ACL·TLS를 갖춘 브로커를 추가했다(§4-1). 클라이언트 인증서(mTLS) 인증, 브로커 이중화는 없다.
