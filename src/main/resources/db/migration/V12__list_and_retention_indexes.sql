-- Stage 31: 목록 페이지네이션·아웃박스 릴레이·보관 정책용 인덱스 (docs/ERD.md §5)
-- 기존에는 FK에 딸린 단일 컬럼 인덱스만 있어, 상태 필터 + 최신순 정렬이나 PENDING 폴링이 테이블을 훑었다.

-- OutboxPublisher: WHERE status = 'PENDING' ORDER BY id ... FOR UPDATE SKIP LOCKED
CREATE INDEX idx_outbox_events_status_id ON outbox_events (status, id);
-- DataRetentionService: WHERE status = 'PUBLISHED' AND created_at < ?
CREATE INDEX idx_outbox_events_status_created ON outbox_events (status, created_at);

-- 상태 이력: WHERE robot_id = ? ORDER BY reported_at DESC LIMIT ?
CREATE INDEX idx_robot_state_snapshots_robot_reported ON robot_state_snapshots (robot_id, reported_at);
-- DataRetentionService: WHERE received_at < ?
CREATE INDEX idx_robot_state_snapshots_received ON robot_state_snapshots (received_at);

-- 관리자 작업 목록: (siteId, status) 필터 + created_at 최신순
CREATE INDEX idx_tasks_site_status_created ON tasks (site_id, status, created_at);
CREATE INDEX idx_tasks_status_created ON tasks (status, created_at);

-- 알람 목록: status 필터 + created_at 최신순
CREATE INDEX idx_alarms_status_created ON alarms (status, created_at);

-- 작업장 수용량 검사: resource_type/resource_id/status로 HELD 예약 수 세기
CREATE INDEX idx_resource_reservations_resource ON resource_reservations (resource_type, resource_id, status);
