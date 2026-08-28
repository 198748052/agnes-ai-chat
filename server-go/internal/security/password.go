package security

import (
	"crypto/hmac"
	"crypto/pbkdf2"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
)

const (
	PasswordIterations = 100_000
	PasswordKeyLen     = 32
	SaltLen            = 16
)

func NewSaltAndHash(password string) (saltHex, hashHex string) {
	salt := make([]byte, SaltLen)
	if _, err := rand.Read(salt); err != nil {
		panic(err)
	}
	return hex.EncodeToString(salt), HashPassword(password, hex.EncodeToString(salt))
}

func HashPassword(password, saltHex string) string {
	salt, err := hex.DecodeString(saltHex)
	if err != nil {
		return ""
	}
	key, err := pbkdf2.Key(sha256.New, password, salt, PasswordIterations, PasswordKeyLen)
	if err != nil {
		return ""
	}
	return hex.EncodeToString(key)
}

func VerifyPassword(password, saltHex, expectedHex string) bool {
	got := HashPassword(password, saltHex)
	return hmac.Equal([]byte(got), []byte(expectedHex))
}
