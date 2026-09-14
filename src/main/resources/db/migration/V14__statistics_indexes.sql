-- Stage 34: 운영 통계 조회용 인덱스 (docs/ERD.md §5)

-- 기간 안에 종료된 작업: WHERE site_id = ? AND completed_at >= ? AND completed_at < ?
CREATE INDEX idx_tasks_site_completed ON tasks (site_id, completed_at);
-- 기간 안에 생성된 작업: WHERE site_id = ? AND created_at >= ? AND created_at < ?
CREATE INDEX idx_tasks_site_created ON tasks (site_id, created_at);
-- 로봇별 배정 구간: WHERE robot_id IN (...) AND assigned_at < ?
CREATE INDEX idx_task_assignments_robot_assigned ON task_assignments (robot_id, assigned_at);
-- 기간 안에 발생한 알람: WHERE created_at >= ? AND created_at < ?
CREATE INDEX idx_alarms_created ON alarms (created_at);
