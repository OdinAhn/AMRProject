-- Stage 32: 비밀번호 변경 시 그 전에 발급된 접근 토큰을 무효화하기 위한 시각 (docs/ERD.md §4.1)
-- 토큰은 서버에 저장하지 않는 자체 서명 값이라 개별 폐기가 불가능하다. 대신 토큰 발급 시각(iat)이 이 값보다 이전이면
-- TokenAuthenticationFilter가 인증하지 않는다. 한 번도 바꾸지 않았으면 NULL(모든 토큰 유효).

ALTER TABLE users
    ADD COLUMN password_changed_at DATETIME(6) NULL;
