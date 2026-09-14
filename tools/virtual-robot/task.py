"""작업 호출·진행을 명령줄에서 한다(작업자 화면 대신) — 가상 로봇으로 전체 흐름을 스크립트로 확인할 때 쓴다.

    python task.py create --site-code SITE-VR --from IN-1 --to OUT-1 [--priority 9]
    python task.py advance 42     # 지금 가능한 작업자 단계(보내기 또는 하역 완료)를 한 번 진행
    python task.py show 42
    python task.py run --site-code SITE-VR --from IN-1 --to OUT-1   # 호출 → 적재 대기 → 보내기 → 하역 대기 → 완료까지 기다리며 진행

관리자 계정은 AMR_API_USERNAME/AMR_API_PASSWORD(또는 AMR_API_TOKEN). 관리자는 작업자 API도 쓸 수 있다.
"""

from __future__ import annotations

import argparse
import os
import sys
import time
import uuid

from server_api import ApiError, ServerApi

TERMINAL = {"SUCCEEDED", "FAILED", "CANCELED"}


def station_ids(api: ServerApi, site_code: str) -> dict[str, int]:
    site = next((s for s in api.get("/api/admin/sites") if s["code"] == site_code), None)
    if site is None:
        raise SystemExit(f"물류센터 {site_code}가 없습니다(seed.py 먼저)")
    return {station["code"]: station["id"] for station in api.get(f"/api/admin/sites/{site['id']}/stations")}


def create(api: ServerApi, site_code: str, origin: str, destination: str, priority: int) -> dict:
    stations = station_ids(api, site_code)
    task = api.post("/api/worker/tasks",
                    {"originStationId": stations[origin], "destinationStationId": stations[destination], "priority": priority},
                    headers={"Idempotency-Key": str(uuid.uuid4())})
    print(f"작업 {task['id']} 호출: {origin} → {destination} (우선순위 {priority})")
    return task


def describe(task: dict) -> str:
    running = next((step["stepType"] for step in task["steps"] if step["status"] == "RUNNING"), "-")
    return (f"작업 {task['id']}: {task['status']} 로봇={task.get('assignedRobotCode') or '-'} 진행 단계={running} "
            f"가능 동작={task['allowedActions']}")


def advance(api: ServerApi, task_id: int) -> dict:
    task = api.get(f"/api/admin/tasks/{task_id}")
    if "DISPATCH" in task["allowedActions"]:
        task = api.post(f"/api/worker/tasks/{task_id}/dispatch")
        print(f"작업 {task_id}: 보내기")
    elif "CONFIRM_UNLOAD" in task["allowedActions"]:
        task = api.post(f"/api/worker/tasks/{task_id}/confirm-unload")
        print(f"작업 {task_id}: 하역 완료")
    return task


def run(api: ServerApi, site_code: str, origin: str, destination: str, priority: int, timeout: float) -> int:
    task_id = create(api, site_code, origin, destination, priority)["id"]
    deadline = time.monotonic() + timeout
    last = ""
    while time.monotonic() < deadline:
        task = api.get(f"/api/admin/tasks/{task_id}")
        line = describe(task)
        if line != last:
            print(line)
            last = line
        if task["status"] in TERMINAL:
            return 0 if task["status"] == "SUCCEEDED" else 1
        if task["status"] == "RECOVERY_REQUIRED":
            print("복구 필요 — 관리자 화면에서 확인하세요")
            return 1
        if "DISPATCH" in task["allowedActions"] or "CONFIRM_UNLOAD" in task["allowedActions"]:
            advance(api, task_id)
        time.sleep(1)
    print(f"{timeout:.0f}초 안에 끝나지 않았습니다: {last}")
    return 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="작업 호출·진행")
    parser.add_argument("--api-url", default=os.environ.get("AMR_API_URL", "http://localhost:8080"))
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("create", "run"):
        p = sub.add_parser(name)
        p.add_argument("--site-code", default="SITE-VR")
        p.add_argument("--from", dest="origin", default="IN-1")
        p.add_argument("--to", dest="destination", default="OUT-1")
        p.add_argument("--priority", type=int, default=0)
        if name == "run":
            p.add_argument("--timeout", type=float, default=180)
    for name in ("advance", "show"):
        sub.add_parser(name).add_argument("task_id", type=int)
    args = parser.parse_args(argv)

    api = ServerApi(args.api_url, os.environ.get("AMR_API_TOKEN"))
    try:
        if not api.token:
            username, password = os.environ.get("AMR_API_USERNAME"), os.environ.get("AMR_API_PASSWORD")
            if not username or not password:
                print("AMR_API_USERNAME/AMR_API_PASSWORD(또는 AMR_API_TOKEN) 환경 변수가 필요합니다.", file=sys.stderr)
                return 2
            api.login(username, password)
        if args.command == "create":
            create(api, args.site_code, args.origin, args.destination, args.priority)
        elif args.command == "run":
            return run(api, args.site_code, args.origin, args.destination, args.priority, args.timeout)
        elif args.command == "advance":
            print(describe(advance(api, args.task_id)))
        else:
            print(describe(api.get(f"/api/admin/tasks/{args.task_id}")))
    except ApiError as error:
        print(f"서버 API 오류: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
