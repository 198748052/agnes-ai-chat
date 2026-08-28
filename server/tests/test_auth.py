import os

os.environ["DATABASE_URL"] = "sqlite:///./test_app.db"

import pytest
from fastapi.testclient import TestClient

from app.database import Base, SessionLocal, engine
from app.main import app
from app.models import User

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield


def test_register_success():
    resp = client.post(
        "/api/v1/auth/register", json={"username": "alice", "password": "secret1"}
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["username"] == "alice"
    assert "id" in body


def test_register_duplicate_returns_409():
    client.post("/api/v1/auth/register", json={"username": "alice", "password": "secret1"})
    resp = client.post(
        "/api/v1/auth/register", json={"username": "alice", "password": "secret2"}
    )
    assert resp.status_code == 409
    assert "already exists" in resp.json()["detail"]


def test_register_short_password_returns_400():
    resp = client.post(
        "/api/v1/auth/register", json={"username": "bob", "password": "123"}
    )
    assert resp.status_code == 400


def test_register_empty_username_returns_400():
    resp = client.post(
        "/api/v1/auth/register", json={"username": "", "password": "secret1"}
    )
    assert resp.status_code == 400


def test_login_success_returns_jwt():
    client.post("/api/v1/auth/register", json={"username": "carol", "password": "secret1"})
    resp = client.post("/api/v1/auth/login", json={"username": "carol", "password": "secret1"})
    assert resp.status_code == 200
    body = resp.json()
    assert "token" in body
    assert body["user"]["username"] == "carol"


def test_login_wrong_password_returns_401():
    client.post("/api/v1/auth/register", json={"username": "dave", "password": "secret1"})
    resp = client.post("/api/v1/auth/login", json={"username": "dave", "password": "wrong!"})
    assert resp.status_code == 401


def test_login_unknown_user_returns_401():
    resp = client.post("/api/v1/auth/login", json={"username": "nobody", "password": "secret1"})
    assert resp.status_code == 401


def test_password_stored_hashed_not_plaintext():
    client.post("/api/v1/auth/register", json={"username": "eve", "password": "secret1"})
    db = SessionLocal()
    try:
        user = db.query(User).filter(User.username == "eve").first()
        assert user is not None
        assert user.password_hash != "secret1"
        assert user.password_hash.startswith("$2")
    finally:
        db.close()


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def _register_and_login(username="user1", password="secret1"):
    client.post(
        "/api/v1/auth/register", json={"username": username, "password": password}
    )
    resp = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    return resp.json()["token"]


def _auth(token):
    return {"Authorization": f"Bearer {token}"}


def test_login_returns_nickname_and_avatar_url():
    token = _register_and_login()
    resp = client.post("/api/v1/auth/login", json={"username": "user1", "password": "secret1"})
    user = resp.json()["user"]
    assert user["nickname"] == "user1"
    assert user["avatar_url"] is None


def test_update_profile_requires_auth():
    resp = client.put("/api/v1/user/profile", json={"nickname": "NewName"})
    assert resp.status_code == 401
    assert resp.json()["detail"] == "unauthorized"


def test_update_profile_success():
    token = _register_and_login()
    resp = client.put(
        "/api/v1/user/profile", json={"nickname": "新昵称"}, headers=_auth(token)
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["nickname"] == "新昵称"
    assert body["username"] == "user1"


def test_update_profile_too_long_returns_400():
    token = _register_and_login()
    resp = client.put(
        "/api/v1/user/profile",
        json={"nickname": "x" * 21},
        headers=_auth(token),
    )
    assert resp.status_code == 400


def test_update_profile_empty_returns_400():
    token = _register_and_login()
    resp = client.put("/api/v1/user/profile", json={"nickname": ""}, headers=_auth(token))
    assert resp.status_code == 400


def test_change_password_wrong_old_returns_401():
    token = _register_and_login()
    resp = client.post(
        "/api/v1/user/password",
        json={"old_password": "wrong!", "new_password": "newsecret"},
        headers=_auth(token),
    )
    assert resp.status_code == 401
    assert resp.json()["detail"] == "old password incorrect"


def test_change_password_success_and_login_with_new():
    token = _register_and_login()
    resp = client.post(
        "/api/v1/user/password",
        json={"old_password": "secret1", "new_password": "newsecret"},
        headers=_auth(token),
    )
    assert resp.status_code == 200
    old_login = client.post("/api/v1/auth/login", json={"username": "user1", "password": "secret1"})
    assert old_login.status_code == 401
    new_login = client.post("/api/v1/auth/login", json={"username": "user1", "password": "newsecret"})
    assert new_login.status_code == 200


def test_change_password_short_new_returns_400():
    token = _register_and_login()
    resp = client.post(
        "/api/v1/user/password",
        json={"old_password": "secret1", "new_password": "123"},
        headers=_auth(token),
    )
    assert resp.status_code == 400


def test_upload_avatar_requires_auth():
    resp = client.post("/api/v1/user/avatar", json={"avatar_base64": "AAAA"})
    assert resp.status_code == 401


def test_upload_avatar_success_and_served():
    token = _register_and_login()
    fake_image = "FAKEJPEGDATA".encode()
    import base64

    resp = client.post(
        "/api/v1/user/avatar",
        json={"avatar_base64": base64.b64encode(fake_image).decode()},
        headers=_auth(token),
    )
    assert resp.status_code == 200
    avatar_url = resp.json()["avatar_url"]
    assert avatar_url == "/uploads/avatars/1.jpg"
    served = client.get(avatar_url)
    assert served.status_code == 200
    assert served.content == fake_image


def test_upload_avatar_invalid_base64_returns_400():
    token = _register_and_login()
    resp = client.post(
        "/api/v1/user/avatar", json={"avatar_base64": "!!not-base64!!"}, headers=_auth(token)
    )
    assert resp.status_code == 400


def test_stats_requires_auth():
    resp = client.get("/api/v1/user/stats")
    assert resp.status_code == 401


def test_stats_empty_user_returns_zeros():
    token = _register_and_login()
    resp = client.get("/api/v1/user/stats", headers=_auth(token))
    assert resp.status_code == 200
    body = resp.json()
    assert body["image"]["today"] == 0
    assert body["image"]["week"] == 0
    assert body["image"]["month"] == 0
    assert body["image"]["total"] == 0
    assert body["video"]["total"] == 0


def test_generation_requires_auth():
    resp = client.post(
        "/api/v1/generation/image", json={"prompt": "a cat", "mode": "image"}
    )
    assert resp.status_code == 401


def test_generation_records_usage_and_stats_aggregates():
    token = _register_and_login()
    for _ in range(2):
        resp = client.post(
            "/api/v1/generation/image", json={"prompt": "a cat"}, headers=_auth(token)
        )
        assert resp.status_code == 200
        assert resp.json()["task_id"] == "mock-task"
    resp = client.post(
        "/api/v1/generation/video", json={"prompt": "a dog"}, headers=_auth(token)
    )
    assert resp.status_code == 200

    stats = client.get("/api/v1/user/stats", headers=_auth(token)).json()
    assert stats["image"]["today"] == 2
    assert stats["image"]["total"] == 2
    assert stats["video"]["total"] == 1
    assert stats["video"]["today"] == 1


def test_stats_only_counts_current_user():
    token_a = _register_and_login("usera", "secret1")
    token_b = _register_and_login("userb", "secret1")
    resp = client.post(
        "/api/v1/generation/image", json={"prompt": "x"}, headers=_auth(token_a)
    )
    assert resp.status_code == 200
    stats_b = client.get("/api/v1/user/stats", headers=_auth(token_b)).json()
    assert stats_b["image"]["total"] == 0
    assert stats_b["video"]["total"] == 0
