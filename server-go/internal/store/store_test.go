package store

import (
	"path/filepath"
	"testing"
)

func newTestStore(t *testing.T) *Store {
	t.Helper()
	dir := t.TempDir()
	s, err := New(filepath.Join(dir, "users.json"), filepath.Join(dir, "usage.json"))
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func TestCreateAndGetUser(t *testing.T) {
	s := newTestStore(t)
	u, err := s.CreateUser("alice", "alice", "salt", "hash")
	if err != nil {
		t.Fatal(err)
	}
	if u.ID != 1 {
		t.Fatalf("expected id 1, got %d", u.ID)
	}
	got, err := s.GetUserByUsername("alice")
	if err != nil {
		t.Fatal(err)
	}
	if got.Nickname != "alice" {
		t.Fatalf("expected nickname alice, got %s", got.Nickname)
	}
}

func TestDuplicateUsername(t *testing.T) {
	s := newTestStore(t)
	_, err := s.CreateUser("alice", "alice", "s1", "h1")
	if err != nil {
		t.Fatal(err)
	}
	_, err = s.CreateUser("alice", "alice", "s2", "h2")
	if err != ErrUsernameExists {
		t.Fatalf("expected ErrUsernameExists, got %v", err)
	}
}

func TestPersistAcrossReload(t *testing.T) {
	dir := t.TempDir()
	usersPath := filepath.Join(dir, "users.json")
	usagePath := filepath.Join(dir, "usage.json")

	s1, err := New(usersPath, usagePath)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := s1.CreateUser("bob", "bob", "salt", "hash"); err != nil {
		t.Fatal(err)
	}
	if err := s1.AddUsage("bob", "image"); err != nil {
		t.Fatal(err)
	}

	s2, err := New(usersPath, usagePath)
	if err != nil {
		t.Fatal(err)
	}
	u, err := s2.GetUserByUsername("bob")
	if err != nil {
		t.Fatal(err)
	}
	if u.ID != 1 {
		t.Fatalf("expected id 1, got %d", u.ID)
	}
	ts := s2.UsageTimestamps("bob", "image")
	if len(ts) != 1 {
		t.Fatalf("expected 1 usage timestamp, got %d", len(ts))
	}
}

func TestUpdateUser(t *testing.T) {
	s := newTestStore(t)
	if _, err := s.CreateUser("carol", "carol", "salt", "hash"); err != nil {
		t.Fatal(err)
	}
	u, err := s.UpdateUser("carol", func(u *User) { u.Nickname = "Carol Chen" })
	if err != nil {
		t.Fatal(err)
	}
	if u.Nickname != "Carol Chen" {
		t.Fatalf("expected nickname Carol Chen, got %s", u.Nickname)
	}
}

func TestUsageTimestampsFilterByType(t *testing.T) {
	s := newTestStore(t)
	if _, err := s.CreateUser("dave", "dave", "salt", "hash"); err != nil {
		t.Fatal(err)
	}
	if err := s.AddUsage("dave", "image"); err != nil {
		t.Fatal(err)
	}
	if err := s.AddUsage("dave", "video"); err != nil {
		t.Fatal(err)
	}
	imageTs := s.UsageTimestamps("dave", "image")
	videoTs := s.UsageTimestamps("dave", "video")
	if len(imageTs) != 1 || len(videoTs) != 1 {
		t.Fatalf("expected 1 each, got image=%d video=%d", len(imageTs), len(videoTs))
	}
}
