package api

import (
	"net/http"
	"strings"

	"agneschat/server/internal/security"
	"agneschat/server/internal/store"
)

type ctxKey string

const userKey ctxKey = "user"

func (s *Server) extractUser(r *http.Request) (*store.User, bool) {
	header := r.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") {
		return nil, false
	}
	token := strings.TrimPrefix(header, "Bearer ")
	claims, err := security.ParseAccessToken(s.cfg.JWTSecret, token)
	if err != nil {
		return nil, false
	}
	user, err := s.store.GetUserByUsername(claims.Username)
	if err != nil {
		return nil, false
	}
	return user, true
}
