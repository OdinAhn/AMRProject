"""AMR 서버 REST API 클라이언트(표준 라이브러리만 사용).

가상 로봇이 활성 지도와 로봇의 물류센터를 알아내고, seed.py가 시연 데이터를 넣을 때 쓴다. 인증은 관리자 로그인 토큰이다.
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str):
        super().__init__(f"HTTP {status}: {body[:300]}")
        self.status = status
        self.body = body


class ServerApi:
    def __init__(self, base_url: str, token: str | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def login(self, username: str, password: str) -> None:
        response = self.request("POST", "/api/auth/login", {"username": username, "password": password}, auth=False)
        self.token = response["token"]

    def request(self, method: str, path: str, body: dict | None = None, auth: bool = True, headers: dict | None = None):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        if auth and self.token:
            request.add_header("Authorization", f"Bearer {self.token}")
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                raw = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            raise ApiError(error.code, error.read().decode("utf-8", "replace")) from error
        return json.loads(raw) if raw else None

    def get(self, path: str):
        return self.request("GET", path)

    def post(self, path: str, body: dict | None = None, headers: dict | None = None):
        return self.request("POST", path, body, headers=headers)

    def put(self, path: str, body: dict):
        return self.request("PUT", path, body)

    def patch(self, path: str, body: dict):
        return self.request("PATCH", path, body)
