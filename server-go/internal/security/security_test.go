package security

import "testing"

func TestPasswordRoundTrip(t *testing.T) {
	salt, hash := NewSaltAndHash("secret123")
	if !VerifyPassword("secret123", salt, hash) {
		t.Fatal("VerifyPassword should accept the correct password")
	}
	if VerifyPassword("wrong", salt, hash) {
		t.Fatal("VerifyPassword should reject a wrong password")
	}
}

func TestPasswordIsSalted(t *testing.T) {
	salt1, hash1 := NewSaltAndHash("same-password")
	salt2, hash2 := NewSaltAndHash("same-password")
	if salt1 == salt2 {
		t.Fatal("salts should be random")
	}
	if hash1 == hash2 {
		t.Fatal("hashes should differ with different salts")
	}
}

func TestJWT(t *testing.T) {
	secret := "test-secret"
	token, err := CreateAccessToken(secret, 42, "alice", 30)
	if err != nil {
		t.Fatal(err)
	}
	claims, err := ParseAccessToken(secret, token)
	if err != nil {
		t.Fatal(err)
	}
	if claims.Sub != "42" {
		t.Fatalf("expected sub 42, got %s", claims.Sub)
	}
	if claims.Username != "alice" {
		t.Fatalf("expected username alice, got %s", claims.Username)
	}
}

func TestJWTWrongSecret(t *testing.T) {
	token, err := CreateAccessToken("secret-a", 1, "bob", 30)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := ParseAccessToken("secret-b", token); err == nil {
		t.Fatal("token signed with a different secret should be rejected")
	}
}

func TestJWTExpired(t *testing.T) {
	secret := "test-secret"
	token, err := CreateAccessToken(secret, 1, "bob", -1)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := ParseAccessToken(secret, token); err == nil {
		t.Fatal("expired token should be rejected")
	}
}

func TestJWTMalformed(t *testing.T) {
	if _, err := ParseAccessToken("secret", "not-a-jwt"); err == nil {
		t.Fatal("malformed token should be rejected")
	}
}
