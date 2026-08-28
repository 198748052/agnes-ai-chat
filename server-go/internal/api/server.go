package api

import (
	"net/http"

	"agneschat/server/internal/config"
	"agneschat/server/internal/store"
)

type Server struct {
	cfg   config.Config
	store *store.Store
}

func New(cfg config.Config, st *store.Store) *Server {
	return &Server{cfg: cfg, store: st}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", s.handleHealth)

	mux.HandleFunc("POST /api/v1/auth/register", s.handleRegister)
	mux.HandleFunc("POST /api/v1/auth/login", s.handleLogin)
	mux.HandleFunc("POST /api/v1/auth/logout", s.handleLogout)

	mux.HandleFunc("GET /api/v1/announcements/latest", s.handleAnnouncements)
	mux.HandleFunc("GET /api/v1/app/version", s.handleVersion)

	mux.HandleFunc("POST /api/v1/generation/image", func(w http.ResponseWriter, r *http.Request) {
		s.handleGeneration(w, r, "image")
	})
	mux.HandleFunc("POST /api/v1/generation/video", func(w http.ResponseWriter, r *http.Request) {
		s.handleGeneration(w, r, "video")
	})

	mux.HandleFunc("PUT /api/v1/user/profile", s.handleUpdateProfile)
	mux.HandleFunc("POST /api/v1/user/password", s.handleChangePassword)
	mux.HandleFunc("POST /api/v1/user/avatar", s.handleUploadAvatar)
	mux.HandleFunc("GET /api/v1/user/stats", s.handleStats)

	uploads := http.StripPrefix("/uploads/", http.FileServer(http.Dir(s.cfg.UploadsDir)))
	mux.Handle("GET /uploads/", uploads)

	return withCORS(mux)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
