package security

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strconv"
	"strings"
	"time"
)

var (
	ErrInvalidToken = errors.New("invalid token")
	ErrTokenExpired = errors.New("token expired")
)

type Claims struct {
	Sub      string `json:"sub"`
	Username string `json:"username"`
	IAT      int64  `json:"iat"`
	Exp      int64  `json:"exp"`
}

func base64urlEncode(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}

func base64urlDecode(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}

func sign(secret string, signingInput string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(signingInput))
	return base64urlEncode(mac.Sum(nil))
}

// CreateAccessToken 生成 HS256 JWT，payload 与 Python 版保持一致的字段。
func CreateAccessToken(secret string, userID int64, username string, expireMinutes int) (string, error) {
	header := map[string]string{"alg": "HS256", "typ": "JWT"}
	headerJSON, err := json.Marshal(header)
	if err != nil {
		return "", err
	}
	now := time.Now().UTC()
	claims := Claims{
		Sub:      strconv.FormatInt(userID, 10),
		Username: username,
		IAT:      now.Unix(),
		Exp:      now.Add(time.Duration(expireMinutes) * time.Minute).Unix(),
	}
	claimsJSON, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}
	signingInput := base64urlEncode(headerJSON) + "." + base64urlEncode(claimsJSON)
	return signingInput + "." + sign(secret, signingInput), nil
}

// ParseAccessToken 校验 HS256 签名与过期时间，返回 claims。
func ParseAccessToken(secret, token string) (*Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, ErrInvalidToken
	}
	headerAndPayload := parts[0] + "." + parts[1]
	if !hmac.Equal([]byte(sign(secret, headerAndPayload)), []byte(parts[2])) {
		return nil, ErrInvalidToken
	}
	payloadJSON, err := base64urlDecode(parts[1])
	if err != nil {
		return nil, ErrInvalidToken
	}
	var claims Claims
	if err := json.Unmarshal(payloadJSON, &claims); err != nil {
		return nil, ErrInvalidToken
	}
	if claims.Exp < time.Now().UTC().Unix() {
		return nil, ErrTokenExpired
	}
	return &claims, nil
}
