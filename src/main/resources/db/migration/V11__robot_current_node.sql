-- Stage 30: 로봇 현재 위치(지도 노드) — docs/ERD.md §4.4
-- 4단계(V5)에서 지도 스키마 전까지 보류했던 current_node_id를 추가한다. pose(x/y/heading)는 여전히 보류 —
-- 시뮬레이터는 노드 단위로만 위치를 알고, 노드 사이 이동은 화면 표시용 위치 이벤트로만 흘려보낸다.

ALTER TABLE robot_current_states
    ADD COLUMN current_node_id BIGINT NULL,
    ADD CONSTRAINT fk_robot_current_states_node FOREIGN KEY (current_node_id) REFERENCES nodes (id);

ALTER TABLE robot_state_snapshots
    ADD COLUMN current_node_id BIGINT NULL,
    ADD CONSTRAINT fk_robot_state_snapshots_node FOREIGN KEY (current_node_id) REFERENCES nodes (id);
