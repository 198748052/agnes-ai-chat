package config

import (
	"os"
	"strconv"
)

const (
	DefaultPort            = "8000"
	DefaultJWTSecret       = "change-me-in-production"
	DefaultTokenExpireMins = 43200
	DefaultUsersFile       = "data/users.json"
	DefaultUsageFile       = "data/usage.json"
	DefaultUploadsDir      = "uploads"
)

type Config struct {
	Port            string
	JWTSecret       string
	TokenExpireMins int
	UsersFile       string
	UsageFile       string
	UploadsDir      string
}

func Load() Config {
	return Config{
		Port:            getenv("PORT", DefaultPort),
		JWTSecret:       getenv("JWT_SECRET", DefaultJWTSecret),
		TokenExpireMins: getenvInt("TOKEN_EXPIRE_MINUTES", DefaultTokenExpireMins),
		UsersFile:       getenv("USERS_FILE", DefaultUsersFile),
		UsageFile:       getenv("USAGE_FILE", DefaultUsageFile),
		UploadsDir:      getenv("UPLOADS_DIR", DefaultUploadsDir),
	}
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getenvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			return n
		}
	}
	return fallback
}
