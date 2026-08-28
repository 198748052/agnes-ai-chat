package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"agneschat/server/internal/config"
	"agneschat/server/internal/store"
)

func newTestServer(t *testing.T) *Server {
	t.Helper()
	dir := t.TempDir()
	cfg := config.Config{
		Port:            "8000",
		JWTSecret:       "test-secret",
		TokenExpireMins: 60,
		UsersFile:       filepath.Join(dir, "users.json"),
		UsageFile:       filepath.Join(dir, "usage.json"),
		UploadsDir:      filepath.Join(dir, "uploads"),
	}
	st, err := store.New(cfg.UsersFile, cfg.UsageFile)
	if err != nil {
		t.Fatal(err)
	}
	return New(cfg, st)
}

func doJSON(t *testing.T, s *Server, method, path string, body any, token string) (*httptest.ResponseRecorder, map[string]any) {
	t.Helper()
	var buf bytes.Buffer
	if body != nil {
		if err := json.NewEncoder(&buf).Encode(body); err != nil {
			t.Fatal(err)
		}
	}
	req := httptest.NewRequest(method, path, &buf)
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	s.Handler().ServeHTTP(rec, req)

	var out map[string]any
	if rec.Body.Len() > 0 {
		_ = json.Unmarshal(rec.Body.Bytes(), &out)
	}
	return rec, out
}

func registerAndLogin(t *testing.T, s *Server, username, password string) string {
	t.Helper()
	rec, out := doJSON(t, s, "POST", "/api/v1/auth/register", map[string]string{
		"username": username,
		"password": password,
	}, "")
	if rec.Code != http.StatusCreated {
		t.Fatalf("register expected 201, got %d: %s", rec.Code, rec.Body.String())
	}
	if out["id"] == nil || out["nickname"] == nil {
		t.Fatalf("register response missing id/nickname: %v", out)
	}
	rec2, out2 := doJSON(t, s, "POST", "/api/v1/auth/login", map[string]string{
		"username": username,
		"password": password,
	}, "")
	if rec2.Code != http.StatusOK {
		t.Fatalf("login expected 200, got %d: %s", rec2.Code, rec2.Body.String())
	}
	return out2["token"].(string)
}

func TestHealth(t *testing.T) {
	s := newTestServer(t)
	rec, out := doJSON(t, s, "GET", "/health", nil, "")
	if rec.Code != http.StatusOK || out["status"] != "ok" {
		t.Fatalf("health check failed: %d %v", rec.Code, out)
	}
}

func TestRegisterAndLoginFlow(t *testing.T) {
	s := newTestServer(t)
	registerAndLogin(t, s, "alice", "password123")
}

func TestRegisterDuplicate(t *testing.T) {
	s := newTestServer(t)
	_, _ = doJSON(t, s, "POST", "/api/v1/auth/register", map[string]string{"username": "alice", "password": "password123"}, "")
	rec, out := doJSON(t, s, "POST", "/api/v1/auth/register", map[string]string{"username": "alice", "password": "password456"}, "")
	if rec.Code != http.StatusConflict {
		t.Fatalf("expected 409, got %d", rec.Code)
	}
	if out["detail"] != "username already exists" {
		t.Fatalf("expected duplicate detail, got %v", out)
	}
}

func TestRegisterInvalid(t *testing.T) {
	s := newTestServer(t)
	rec, _ := doJSON(t, s, "POST", "/api/v1/auth/register", map[string]string{"username": "a", "password": "123"}, "")
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for short password, got %d", rec.Code)
	}
	rec, _ = doJSON(t, s, "POST", "/api/v1/auth/register", map[string]string{"username": "", "password": "password123"}, "")
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for empty username, got %d", rec.Code)
	}
}

func TestLoginWrongPassword(t *testing.T) {
	s := newTestServer(t)
	registerAndLogin(t, s, "bob", "password123")
	rec, _ := doJSON(t, s, "POST", "/api/v1/auth/login", map[string]string{"username": "bob", "password": "wrong"}, "")
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", rec.Code)
	}
}

func TestAnnouncement(t *testing.T) {
	s := newTestServer(t)
	rec, out := doJSON(t, s, "GET", "/api/v1/announcements/latest", nil, "")
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if out["id"] != "welcome" {
		t.Fatalf("expected welcome announcement, got %v", out)
	}
}

func TestVersion(t *testing.T) {
	s := newTestServer(t)
	rec, out := doJSON(t, s, "GET", "/api/v1/app/version", nil, "")
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if out["latest_version_code"] == nil {
		t.Fatalf("expected version code, got %v", out)
	}
}

func TestGenerationRequiresAuth(t *testing.T) {
	s := newTestServer(t)
	rec, _ := doJSON(t, s, "POST", "/api/v1/generation/image", map[string]string{"prompt": "hi"}, "")
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 without token, got %d", rec.Code)
	}
}

func TestGenerationAndStats(t *testing.T) {
	s := newTestServer(t)
	token := registerAndLogin(t, s, "carol", "password123")

	rec, out := doJSON(t, s, "POST", "/api/v1/generation/image", map[string]string{"prompt": "cat", "mode": "image"}, token)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if out["task_id"] != "mock-task" {
		t.Fatalf("expected mock-task, got %v", out)
	}

	_, _ = doJSON(t, s, "POST", "/api/v1/generation/video", map[string]string{"prompt": "dog", "mode": "video"}, token)

	rec, out = doJSON(t, s, "GET", "/api/v1/user/stats", nil, token)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	image := out["image"].(map[string]any)
	video := out["video"].(map[string]any)
	if image["total"].(float64) != 1 {
		t.Fatalf("expected image total 1, got %v", image)
	}
	if video["total"].(float64) != 1 {
		t.Fatalf("expected video total 1, got %v", video)
	}
}

func TestUpdateProfile(t *testing.T) {
	s := newTestServer(t)
	token := registerAndLogin(t, s, "dave", "password123")
	rec, out := doJSON(t, s, "PUT", "/api/v1/user/profile", map[string]string{"nickname": "Dave Chen"}, token)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if out["nickname"] != "Dave Chen" {
		t.Fatalf("expected new nickname, got %v", out)
	}
}

func TestChangePassword(t *testing.T) {
	s := newTestServer(t)
	token := registerAndLogin(t, s, "erin", "password123")

	rec, _ := doJSON(t, s, "POST", "/api/v1/user/password", map[string]string{
		"old_password": "wrong-old",
		"new_password": "newpassword",
	}, token)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for wrong old password, got %d", rec.Code)
	}

	rec, _ = doJSON(t, s, "POST", "/api/v1/user/password", map[string]string{
		"old_password": "password123",
		"new_password": "newpassword",
	}, token)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}

	rec, _ = doJSON(t, s, "POST", "/api/v1/auth/login", map[string]string{"username": "erin", "password": "newpassword"}, "")
	if rec.Code != http.StatusOK {
		t.Fatalf("login with new password should succeed, got %d", rec.Code)
	}
}

func TestUploadAvatar(t *testing.T) {
	s := newTestServer(t)
	token := registerAndLogin(t, s, "frank", "password123")
	b64 := base64EncodedImage()
	rec, out := doJSON(t, s, "POST", "/api/v1/user/avatar", map[string]string{"avatar_base64": b64}, token)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if out["avatar_url"] == nil {
		t.Fatalf("expected avatar_url, got %v", out)
	}
	url := out["avatar_url"].(string)
	if url == "" || url[:9] != "/uploads/" {
		t.Fatalf("unexpected avatar_url: %s", url)
	}

	rec, _ = doJSON(t, s, "POST", "/api/v1/user/avatar", map[string]string{"avatar_base64": "not-base64!!!"}, token)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for invalid image, got %d", rec.Code)
	}
}

func TestLogout(t *testing.T) {
	s := newTestServer(t)
	rec, _ := doJSON(t, s, "POST", "/api/v1/auth/logout", nil, "")
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
}
