"""가상 로봇 시연용 데이터를 서버 API로 한 번에 넣는다.

만드는 것(물류센터 코드 --site-code, 기본 SITE-VR):
- 3×3 격자 지도(노드 A1~C3, 이웃 노드끼리 양방향 통로) — 검증·활성화까지
- 작업장: 입고 IN-1(A1), IN-2(A3), 출고 OUT-1(C3), OUT-2(B3), 충전 CHG-1(C1) — 지도 노드에 연결
- 로봇 VR-01..(--robots개), 운영 중(IN_SERVICE)

같은 코드의 물류센터가 이미 있으면 아무것도 바꾸지 않고 끝낸다. 관리자 계정은 AMR_API_USERNAME/AMR_API_PASSWORD(또는 AMR_API_TOKEN).
"""

from __future__ import annotations

import argparse
import os
import sys

from server_api import ApiError, ServerApi

GRID = {"A": 10, "B": 50, "C": 90}  # 열 → x
ROWS = {"1": 10, "2": 50, "3": 90}  # 행 → y
STATIONS = [
    ("IN-1", "입고 1", "PICKUP", "A1"),
    ("IN-2", "입고 2", "PICKUP", "A3"),
    ("OUT-1", "출고 1", "DROPOFF", "C3"),
    ("OUT-2", "출고 2", "DROPOFF", "B3"),
    ("CHG-1", "충전소", "CHARGING", "C1"),
]
NODE_TYPES = {"A1": "STATION", "A3": "STATION", "C3": "STATION", "B3": "STATION", "C1": "CHARGING"}


def seed(api: ServerApi, site_code: str, robot_count: int, robot_prefix: str) -> None:
    if any(site["code"] == site_code for site in api.get("/api/admin/sites")):
        print(f"물류센터 {site_code}가 이미 있어 아무것도 바꾸지 않습니다.")
        return
    site = api.post("/api/admin/sites", {"code": site_code, "name": "가상 로봇 시연 센터"})
    site_id = site["id"]

    map_entity = api.post(f"/api/admin/sites/{site_id}/maps", {"name": "3x3 격자 지도"})
    version = api.post(f"/api/admin/maps/{map_entity['id']}/versions", {"filePath": None, "coordinateSystem": "LOCAL_XY"})
    version_id = version["id"]
    node_ids: dict[str, int] = {}
    for column, x in GRID.items():
        for row, y in ROWS.items():
            code = column + row
            node = api.post(f"/api/admin/map-versions/{version_id}/nodes",
                            {"code": code, "x": x, "y": y, "nodeType": NODE_TYPES.get(code, "WAYPOINT")})
            node_ids[code] = node["id"]
    columns, rows = list(GRID), list(ROWS)
    for ci, column in enumerate(columns):
        for ri, row in enumerate(rows):
            if ci + 1 < len(columns):
                connect(api, version_id, node_ids[column + row], node_ids[columns[ci + 1] + row])
            if ri + 1 < len(rows):
                connect(api, version_id, node_ids[column + row], node_ids[column + rows[ri + 1]])
    api.post(f"/api/admin/map-versions/{version_id}/validate")
    api.post(f"/api/admin/map-versions/{version_id}/activate")

    for code, name, station_type, node_code in STATIONS:
        station = api.post(f"/api/admin/sites/{site_id}/stations", {"code": code, "name": name, "stationType": station_type})
        api.put(f"/api/admin/stations/{station['id']}/map-binding", {"mapVersionId": version_id, "nodeId": node_ids[node_code]})

    robot_codes = []
    for index in range(1, robot_count + 1):
        code = f"{robot_prefix}{index:02d}"
        robot = api.post("/api/admin/robots", {"code": code, "name": f"가상 로봇 {index}", "model": "VDA5050-SIM",
                                               "manufacturer": "VirtualRobot", "siteId": site_id})
        api.patch(f"/api/admin/robots/{robot['id']}/lifecycle-status", {"lifecycleStatus": "IN_SERVICE"})
        robot_codes.append(code)

    print(f"물류센터 {site_code}(id={site_id}), 지도 버전 {version_id}(노드 9·통로 12), 작업장 {len(STATIONS)}개, 로봇 {', '.join(robot_codes)}를 만들었습니다.")
    print("다음: 서버를 MQTT 모드(ROBOT_GATEWAY_MQTT_ENABLED=true)로 띄운 뒤")
    print(f"  python virtual_robot.py --robots {','.join(robot_codes)}")


def connect(api: ServerApi, version_id: int, from_id: int, to_id: int) -> None:
    api.post(f"/api/admin/map-versions/{version_id}/edges",
             {"fromNodeId": from_id, "toNodeId": to_id, "direction": "TWO_WAY", "distance": None, "maxCapacity": None})


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="가상 로봇 시연 데이터 넣기")
    parser.add_argument("--api-url", default=os.environ.get("AMR_API_URL", "http://localhost:8080"))
    parser.add_argument("--site-code", default="SITE-VR")
    parser.add_argument("--robots", type=int, default=3)
    parser.add_argument("--robot-prefix", default="VR-")
    args = parser.parse_args(argv)

    api = ServerApi(args.api_url, os.environ.get("AMR_API_TOKEN"))
    try:
        if not api.token:
            username, password = os.environ.get("AMR_API_USERNAME"), os.environ.get("AMR_API_PASSWORD")
            if not username or not password:
                print("AMR_API_USERNAME/AMR_API_PASSWORD(또는 AMR_API_TOKEN) 환경 변수가 필요합니다.", file=sys.stderr)
                return 2
            api.login(username, password)
        seed(api, args.site_code, args.robots, args.robot_prefix)
    except (ApiError, OSError) as error:
        print(f"서버 API 호출 실패: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
