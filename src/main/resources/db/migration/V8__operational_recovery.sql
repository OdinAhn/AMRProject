-- Stage 8: 운영 장애 복구 (취소 완결, 작업 불가 보고)
-- 참고: docs/ERD.md §4.5 task_events.payload_json

ALTER TABLE task_events
    ADD COLUMN payload TEXT;
