package api

import (
	"net/http"

	"agneschat/server/internal/security"
	"agneschat/server/internal/store"
)

type registerRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type loginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type userOut struct {
	ID        string `json:"id"`
	Username  string `json:"username"`
	Nickname  string `json:"nickname"`
	AvatarURL string `json:"avatar_url,omitempty"`
}

type loginResponse struct {
	Token string  `json:"token"`
	User  userOut `json:"user"`
}

func toUserOut(u *store.User) userOut {
	return userOut{
		ID:        itoa(u.ID),
		Username:  u.Username,
		Nickname:  u.Nickname,
		AvatarURL: u.AvatarURL,
	}
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if !decodeBody(w, r, &req) {
		return
	}
	if len(req.Username) < 1 || len(req.Username) > 64 || len(req.Password) < 6 {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	salt, hash := security.NewSaltAndHash(req.Password)
	user, err := s.store.CreateUser(req.Username, req.Username, salt, hash)
	if err == store.ErrUsernameExists {
		writeError(w, http.StatusConflict, "username already exists")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusCreated, toUserOut(user))
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if !decodeBody(w, r, &req) {
		return
	}
	if req.Username == "" || req.Password == "" {
		writeError(w, http.StatusUnauthorized, "invalid credentials")
		return
	}
	user, err := s.store.GetUserByUsername(req.Username)
	if err != nil || !security.VerifyPassword(req.Password, user.Salt, user.PasswordHash) {
		writeError(w, http.StatusUnauthorized, "invalid credentials")
		return
	}
	token, err := security.CreateAccessToken(s.cfg.JWTSecret, int64(user.ID), user.Username, s.cfg.TokenExpireMins)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, loginResponse{Token: token, User: toUserOut(user)})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"detail": "ok"})
}
