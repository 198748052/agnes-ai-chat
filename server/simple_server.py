#!/usr/bin/env python3
import argparse
import base64
import hashlib
import hmac
import json
import os
import secrets
import threading
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

DEFAULT_PORT = 8000
DATA_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "users.json")
USAGE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "usage.json")
PASSWORD_ITERATIONS = 100_000
DATA_LOCK = threading.Lock()
MAX_BODY_BYTES = 1024 * 1024
MAX_AVATAR_BODY_BYTES = 6 * 1024 * 1024
UPLOADS_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")
AVATAR_DIR = os.path.join(UPLOADS_ROOT, "avatars")

# 登录后签发的 token -> username（内存态，重启失效需重新登录）
TOKENS = {}
MAX_NICKNAME_LENGTH = 20

ANNOUNCEMENTS = [
    {
        "id": "welcome",
        "title": "欢迎使用 Agnes AI Chat",
        "content": "Agnes AI Chat 是一款基于 Agnes 2.5 Flash 大模型的安卓聊天应用，支持实时流式对话、多轮上下文、聊天记录本地持久化，以及 AI 图片 / 视频创作。",
        "priority": "important",
        "publish_at": 0,
    },
    {
        "id": "api-key",
        "title": "使用前请先配置 API Key",
        "content": "首次使用请在「我的」-「设置」中填入你的 Agnes API Key，并根据需要自定义系统提示词。",
        "priority": "normal",
        "publish_at": 0,
    },
]

VERSION_INFO = {
    "latest_version_code": 1,
    "latest_version_name": "1.0",
    "force_update": False,
    "update_log": "",
    "download_url": "",
}


def load_users():
    if not os.path.exists(DATA_FILE):
        return {"next_id": 1, "users": {}}
    with open(DATA_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_users(data):
    tmp = DATA_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, DATA_FILE)


def load_usage():
    if not os.path.exists(USAGE_FILE):
        return {"next_id": 1, "records": []}
    with open(USAGE_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_usage(data):
    tmp = USAGE_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, USAGE_FILE)


def hash_password(password, salt_hex):
    salt = bytes.fromhex(salt_hex)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PASSWORD_ITERATIONS)
    return digest.hex()


def new_password_hash(password):
    salt = secrets.token_bytes(16)
    return salt.hex(), hash_password(password, salt.hex())


def verify_password(password, salt_hex, expected_hash):
    return hmac.compare_digest(hash_password(password, salt_hex), expected_hash)


def user_out(user):
    return {
        "id": str(user["id"]),
        "username": user["username"],
        "nickname": user.get("nickname", user["username"]),
        "avatar_url": user.get("avatar_url"),
    }


def do_health(body, auth=None):
    return 200, {"status": "ok"}


def do_register(body, auth=None):
    if not isinstance(body, dict):
        return 400, {"detail": "invalid request"}
    username = body.get("username")
    password = body.get("password")
    if (
        not isinstance(username, str)
        or not 1 <= len(username) <= 64
        or not isinstance(password, str)
        or len(password) < 6
    ):
        return 400, {"detail": "invalid request"}
    with DATA_LOCK:
        data = load_users()
        if username in data["users"]:
            return 409, {"detail": "username already exists"}
        salt, password_hash = new_password_hash(password)
        user = {
            "id": data["next_id"],
            "username": username,
            "nickname": username,
            "salt": salt,
            "password_hash": password_hash,
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        data["users"][username] = user
        data["next_id"] += 1
        save_users(data)
        return 201, user_out(user)


def do_login(body, auth=None):
    if not isinstance(body, dict):
        return 400, {"detail": "invalid request"}
    username = body.get("username")
    password = body.get("password")
    if not isinstance(username, str) or not isinstance(password, str) or not username or not password:
        return 401, {"detail": "invalid credentials"}
    with DATA_LOCK:
        data = load_users()
        user = data["users"].get(username)
    if user is None or not verify_password(password, user["salt"], user["password_hash"]):
        return 401, {"detail": "invalid credentials"}
    token = secrets.token_urlsafe(32)
    TOKENS[token] = user["username"]
    return 200, {"token": token, "user": user_out(user)}


def do_logout(body, auth=None):
    return 200, {"detail": "ok"}


def do_announcements(body, auth=None):
    # 无公告时返回 JSON null，客户端将视为空列表
    if not ANNOUNCEMENTS:
        return 200, None
    return 200, ANNOUNCEMENTS[0]


def do_version(body, auth=None):
    return 200, VERSION_INFO


def do_generation(body, auth=None, gen_type=None):
    if auth is None:
        return 401, {"detail": "unauthorized"}
    with DATA_LOCK:
        usage = load_usage()
        record = {
            "id": usage["next_id"],
            "username": auth,
            "type": gen_type or "image",
            "ts": int(datetime.now(timezone.utc).timestamp() * 1000),
        }
        usage["next_id"] += 1
        usage["records"].append(record)
        save_usage(usage)
    return 200, {"task_id": "mock-task", "media_url": None}


def _period_counts(timestamps_ms):
    """按服务端 UTC 时区聚合今日 / 本周 / 本月 / 累计计数。"""
    now = datetime.now(timezone.utc)
    today_start = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    week_base = now - timedelta(days=now.weekday())
    week_start = datetime(week_base.year, week_base.month, week_base.day, tzinfo=timezone.utc)
    month_start = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
    today = week = month = 0
    for ts in timestamps_ms:
        dt = datetime.fromtimestamp(ts / 1000, tz=timezone.utc)
        if dt >= today_start:
            today += 1
        if dt >= week_start:
            week += 1
        if dt >= month_start:
            month += 1
    return {"today": today, "week": week, "month": month, "total": len(timestamps_ms)}


def do_stats(body, auth=None):
    if auth is None:
        return 401, {"detail": "unauthorized"}
    with DATA_LOCK:
        usage = load_usage()
        image_ts = [
            r["ts"] for r in usage["records"] if r["username"] == auth and r["type"] == "image"
        ]
        video_ts = [
            r["ts"] for r in usage["records"] if r["username"] == auth and r["type"] == "video"
        ]
    return 200, {
        "image": _period_counts(image_ts),
        "video": _period_counts(video_ts),
    }


def do_update_profile(body, auth=None):
    if auth is None:
        return 401, {"detail": "unauthorized"}
    if not isinstance(body, dict):
        return 400, {"detail": "invalid request"}
    nickname = body.get("nickname")
    if (
        not isinstance(nickname, str)
        or len(nickname) < 1
        or len(nickname) > MAX_NICKNAME_LENGTH
    ):
        return 400, {"detail": "invalid request"}
    with DATA_LOCK:
        data = load_users()
        user = data["users"].get(auth)
        if user is None:
            return 401, {"detail": "unauthorized"}
        user["nickname"] = nickname
        save_users(data)
        return 200, user_out(user)


def do_change_password(body, auth=None):
    if auth is None:
        return 401, {"detail": "unauthorized"}
    if not isinstance(body, dict):
        return 400, {"detail": "invalid request"}
    old_password = body.get("old_password")
    new_password = body.get("new_password")
    if (
        not isinstance(old_password, str)
        or not isinstance(new_password, str)
        or len(new_password) < 6
    ):
        return 400, {"detail": "invalid request"}
    with DATA_LOCK:
        data = load_users()
        user = data["users"].get(auth)
        if user is None:
            return 401, {"detail": "unauthorized"}
        if not verify_password(old_password, user["salt"], user["password_hash"]):
            return 401, {"detail": "old password incorrect"}
        salt, password_hash = new_password_hash(new_password)
        user["salt"] = salt
        user["password_hash"] = password_hash
        save_users(data)
        return 200, {"detail": "ok"}


def do_upload_avatar(body, auth=None):
    if auth is None:
        return 401, {"detail": "unauthorized"}
    if not isinstance(body, dict):
        return 400, {"detail": "invalid request"}
    avatar_base64 = body.get("avatar_base64")
    if not isinstance(avatar_base64, str) or not avatar_base64:
        return 400, {"detail": "invalid request"}
    try:
        data = base64.b64decode(avatar_base64, validate=True)
    except Exception:
        return 400, {"detail": "invalid image"}
    if len(data) > 5 * 1024 * 1024:
        return 413, {"detail": "image too large"}
    with DATA_LOCK:
        data_store = load_users()
        user = data_store["users"].get(auth)
        if user is None:
            return 401, {"detail": "unauthorized"}
        os.makedirs(AVATAR_DIR, exist_ok=True)
        avatar_path = os.path.join(AVATAR_DIR, f"{user['id']}.jpg")
        with open(avatar_path, "wb") as f:
            f.write(data)
        avatar_url = f"/uploads/avatars/{user['id']}.jpg"
        user["avatar_url"] = avatar_url
        save_users(data_store)
        return 200, {"avatar_url": avatar_url}


ROUTES = {
    ("POST", "/api/v1/auth/register"): do_register,
    ("POST", "/api/v1/auth/login"): do_login,
    ("POST", "/api/v1/auth/logout"): do_logout,
    ("GET", "/api/v1/announcements/latest"): do_announcements,
    ("GET", "/api/v1/app/version"): do_version,
    ("POST", "/api/v1/generation/image"): lambda b, a=None: do_generation(b, a, "image"),
    ("POST", "/api/v1/generation/video"): lambda b, a=None: do_generation(b, a, "video"),
    ("PUT", "/api/v1/user/profile"): do_update_profile,
    ("POST", "/api/v1/user/password"): do_change_password,
    ("POST", "/api/v1/user/avatar"): do_upload_avatar,
    ("GET", "/api/v1/user/stats"): do_stats,
    ("GET", "/health"): do_health,
}


class Handler(BaseHTTPRequestHandler):
    server_version = "AgnesSimpleServer/1.0"

    def _send(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return None

    def _resolve_auth(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return None
        token = header[len("Bearer "):].strip()
        return TOKENS.get(token)

    def _handle(self):
        path = urlparse(self.path).path
        handler = ROUTES.get((self.command, path))
        if handler is None:
            self._send(404, {"detail": "not found"})
            return
        if self.command in ("POST", "PUT"):
            raw_length = int(self.headers.get("Content-Length") or 0)
            limit = MAX_AVATAR_BODY_BYTES if path == "/api/v1/user/avatar" else MAX_BODY_BYTES
            if raw_length > limit:
                self._send(413, {"detail": "request entity too large"})
                return
            body = self._read_body()
        else:
            body = None
        auth = self._resolve_auth()
        try:
            code, payload = handler(body, auth)
        except Exception:
            # 兜底：业务处理异常时返回 500 JSON，避免连接无响应断开
            self._send(500, {"detail": "internal server error"})
            return
        self._send(code, payload)

    def _serve_static(self, path):
        rel = path[len("/uploads/"):]
        full = os.path.realpath(os.path.join(UPLOADS_ROOT, rel))
        if not full.startswith(os.path.realpath(UPLOADS_ROOT) + os.sep):
            self._send(403, {"detail": "forbidden"})
            return
        if not os.path.isfile(full):
            self._send(404, {"detail": "not found"})
            return
        with open(full, "rb") as f:
            data = f.read()
        self.send_response(200)
        self.send_header("Content-Type", "image/jpeg")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlparse(self.path).path
        if path.startswith("/uploads/"):
            self._serve_static(path)
            return
        self._handle()

    def do_POST(self):
        self._handle()

    def do_PUT(self):
        self._handle()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()


def main():
    global DATA_FILE, USAGE_FILE
    parser = argparse.ArgumentParser(description="Agnes AI Chat 简易测试服务器（Python 标准库，零依赖）")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="监听端口，默认 8000")
    parser.add_argument("--data", default=DATA_FILE, help="用户数据文件路径，默认 users.json")
    parser.add_argument("--usage", default=USAGE_FILE, help="用量记录文件路径，默认 usage.json")
    args = parser.parse_args()
    DATA_FILE = args.data
    USAGE_FILE = args.usage
    with DATA_LOCK:
        data = load_users()
        data.setdefault("next_id", 1)
        data.setdefault("users", {})
        save_users(data)
        usage = load_usage()
        usage.setdefault("next_id", 1)
        usage.setdefault("records", [])
        save_usage(usage)
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print("Agnes 简易测试服务器已启动: http://0.0.0.0:%d" % args.port)
    print("用户数据文件: %s" % DATA_FILE)
    print("用量记录文件: %s" % USAGE_FILE)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
