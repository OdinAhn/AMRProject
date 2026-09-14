#!/bin/sh
# 44단계: 운영용 MQTT 브로커(mosquitto 2) 설정을 기동 시 환경 변수·파일로 만든다 (docs/DEPLOYMENT.md §4-1).
#
# 필수 환경 변수
#   MQTT_SERVER_USERNAME / MQTT_SERVER_PASSWORD  관제 서버 계정(모든 로봇 토픽 읽기·쓰기)
#   MQTT_MANUFACTURER                            VDA 5050 토픽의 manufacturer (서버 설정과 같아야 함, 기본 AMRProject)
# 파일(선택)
#   /amr/robot-credentials   "시리얼번호:비밀번호" 한 줄에 하나. 로봇 계정 이름 = 시리얼번호(= 서버에 등록한 로봇 코드)
#   /amr/certs/server.crt, server.key  있으면 로봇용 TLS 리스너(8883)를 연다
#
# 리스너
#   1883  평문 — 같은 도커 네트워크의 관제 서버 전용. 호스트에 포트를 열지 않는다.
#   8883  TLS — 로봇 접속용(인증서가 있을 때만)
set -eu
# 비밀번호 해시·ACL 파일이 처음부터 다른 사용자에게 읽히지 않게 만든다
umask 077

: "${MQTT_SERVER_USERNAME:?MQTT_SERVER_USERNAME이 필요합니다}"
: "${MQTT_SERVER_PASSWORD:?MQTT_SERVER_PASSWORD가 필요합니다}"
MANUFACTURER="${MQTT_MANUFACTURER:-AMRProject}"
CONF_DIR=/mosquitto/generated
mkdir -p "$CONF_DIR" /mosquitto/data

# 계정: 서버 + 로봇들. 비밀번호는 해시로만 저장된다.
PASSWD="$CONF_DIR/passwd"
: > "$PASSWD"
mosquitto_passwd -b "$PASSWD" "$MQTT_SERVER_USERNAME" "$MQTT_SERVER_PASSWORD"
ROBOT_COUNT=0
if [ -f /amr/robot-credentials ]; then
  # CRLF로 저장된 파일도 받는다
  tr -d '\r' < /amr/robot-credentials | while IFS=: read -r serial password; do
    case "$serial" in ''|'#'*) continue ;; esac
    if [ -z "$password" ]; then
      echo "robot-credentials: $serial 의 비밀번호가 비어 있어 건너뜁니다" >&2
      continue
    fi
    mosquitto_passwd -b "$PASSWD" "$serial" "$password"
  done
  ROBOT_COUNT=$(tr -d '\r' < /amr/robot-credentials | grep -cv '^[[:space:]]*\(#\|$\)' || true)
fi

# 토픽 권한: 서버는 manufacturer 아래 전부, 로봇은 자기 시리얼번호(%u) 토픽만.
ACL="$CONF_DIR/acl"
cat > "$ACL" <<EOF
user $MQTT_SERVER_USERNAME
topic readwrite uagv/v2/$MANUFACTURER/#

# 로봇(계정 이름 = 시리얼번호)은 자기에게 온 주문·즉시 동작만 읽고, 자기 상태만 보낸다
pattern read uagv/v2/$MANUFACTURER/%u/order
pattern read uagv/v2/$MANUFACTURER/%u/instantActions
pattern write uagv/v2/$MANUFACTURER/%u/state
pattern write uagv/v2/$MANUFACTURER/%u/connection
pattern write uagv/v2/$MANUFACTURER/%u/visualization
pattern write uagv/v2/$MANUFACTURER/%u/factsheet
EOF

CONF="$CONF_DIR/mosquitto.conf"
cat > "$CONF" <<EOF
per_listener_settings false
allow_anonymous false
password_file $PASSWD
acl_file $ACL
persistence true
persistence_location /mosquitto/data/
log_dest stdout
log_type error
log_type warning
log_type notice
# 접속·해제·인증 실패 기록(운영 추적용)
log_type information

listener 1883
EOF

if [ -f /amr/certs/server.crt ] && [ -f /amr/certs/server.key ]; then
  cat >> "$CONF" <<EOF

listener 8883
certfile /amr/certs/server.crt
keyfile /amr/certs/server.key
tls_version tlsv1.2
EOF
  TLS=on
else
  echo "경고: /amr/certs/server.crt·server.key가 없어 로봇용 TLS 리스너(8883)를 열지 않습니다" >&2
  TLS=off
fi

chown -R mosquitto:mosquitto "$CONF_DIR" /mosquitto/data 2>/dev/null || true
chmod 0700 "$CONF_DIR"
chmod 0600 "$PASSWD" "$ACL"
echo "mosquitto 설정 생성: manufacturer=$MANUFACTURER, 로봇 계정 ${ROBOT_COUNT}개, TLS=$TLS"
exec mosquitto -c "$CONF"
