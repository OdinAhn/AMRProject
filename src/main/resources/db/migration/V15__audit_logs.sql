-- Stage 37: 감사 로그 (docs/ERD.md §4.10 audit_logs)
-- 누가(actor) 무엇을(action) 어디에(target) 했고 결과(http_status)가 어땠는지를 append-only로 남긴다.
-- 요청 본문은 비밀번호가 들어갈 수 있어 저장하지 않는다(payload_json에는 경로 변수·쿼리·생성된 id만).
CREATE TABLE audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id   BIGINT,
    -- 없는 아이디로 로그인 실패한 경우나 외부 시스템(API 키)처럼 users 행이 없는 행위자도 이름은 남긴다
    actor_username  VARCHAR(100),
    action          VARCHAR(150) NOT NULL,
    target_type     VARCHAR(30),
    target_id       BIGINT,
    http_status     INT,
    payload_json    TEXT,
    ip_address      VARCHAR(45),
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs (actor_username, created_at);
CREATE INDEX idx_audit_logs_target_created ON audit_logs (target_type, target_id, created_at);
