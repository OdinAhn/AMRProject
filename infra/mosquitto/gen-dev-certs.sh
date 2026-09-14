#!/bin/sh
# 44단계: 로봇용 MQTT TLS 리스너에 쓸 **개발·시험용** 자체 서명 인증서를 만든다.
# 운영에서는 조직의 인증 기관(또는 공인 인증서)이 발급한 인증서를 infra/mosquitto/certs/에 두세요.
#
#   sh infra/mosquitto/gen-dev-certs.sh [추가 호스트 이름 또는 IP ...]
#
# 만드는 파일(infra/mosquitto/certs/, 커밋하지 않음): ca.crt(로봇·서버가 신뢰할 CA), server.crt, server.key
set -eu
# Windows Git Bash가 "/CN=..."를 경로로 바꾸지 않게 한다(리눅스·macOS에서는 영향 없음)
export MSYS_NO_PATHCONV=1
DIR="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$DIR"
cd "$DIR"

SAN="DNS:localhost,DNS:mosquitto,IP:127.0.0.1"
for name in "$@"; do
  case "$name" in
    *[!0-9.]*) SAN="$SAN,DNS:$name" ;;
    *) SAN="$SAN,IP:$name" ;;
  esac
done

openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj "/CN=AMR Dev MQTT CA" \
  -keyout ca.key -out ca.crt
openssl req -newkey rsa:2048 -nodes -subj "/CN=mosquitto" -keyout server.key -out server.csr
printf "subjectAltName=%s\nextendedKeyUsage=serverAuth\n" "$SAN" > server.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 825 \
  -extfile server.ext -out server.crt
rm -f server.csr server.ext ca.srl
chmod 644 server.key
echo "인증서를 만들었습니다: $DIR (SAN: $SAN)"
