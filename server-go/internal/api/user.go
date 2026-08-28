package api

import (
	"encoding/base64"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"agneschat/server/internal/security"
	"agneschat/server/internal/store"
)

const maxNicknameLength = 20

type updateProfileRequest struct {
	Nickname string `json:"nickname"`
}

type changePasswordRequest struct {
	OldPassword string `json:"old_password"`
	NewPassword string `json:"new_password"`
}

type uploadAvatarRequest struct {
	AvatarBase64 string `json:"avatar_base64"`
}

type avatarResponse struct {
	AvatarURL string `json:"avatar_url"`
}

type periodCounts struct {
	Today int `json:"today"`
	Week  int `json:"week"`
	Month int `json:"month"`
	Total int `json:"total"`
}

type userStats struct {
	Image periodCounts `json:"image"`
	Video periodCounts `json:"video"`
}

func (s *Server) handleUpdateProfile(w http.ResponseWriter, r *http.Request) {
	user, ok := s.extractUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	var req updateProfileRequest
	if !decodeBody(w, r, &req) {
		return
	}
	if len(req.Nickname) < 1 || len(req.Nickname) > maxNicknameLength {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	updated, err := s.store.UpdateUser(user.Username, func(u *store.User) { u.Nickname = req.Nickname })
	if err != nil {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	writeJSON(w, http.StatusOK, toUserOut(updated))
}

func (s *Server) handleChangePassword(w http.ResponseWriter, r *http.Request) {
	user, ok := s.extractUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	var req changePasswordRequest
	if !decodeBody(w, r, &req) {
		return
	}
	if len(req.NewPassword) < 6 {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	if !security.VerifyPassword(req.OldPassword, user.Salt, user.PasswordHash) {
		writeError(w, http.StatusUnauthorized, "old password incorrect")
		return
	}
	salt, hash := security.NewSaltAndHash(req.NewPassword)
	if _, err := s.store.UpdateUser(user.Username, func(u *store.User) {
		u.Salt = salt
		u.PasswordHash = hash
	}); err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"detail": "ok"})
}

func (s *Server) handleUploadAvatar(w http.ResponseWriter, r *http.Request) {
	user, ok := s.extractUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	raw, err := readBodyBytes(r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	var req uploadAvatarRequest
	if err := jsonUnmarshal(raw, &req); err != nil || req.AvatarBase64 == "" {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	data, err := base64.StdEncoding.DecodeString(req.AvatarBase64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid image")
		return
	}
	if len(data) > 5*1024*1024 {
		writeError(w, http.StatusRequestEntityTooLarge, "image too large")
		return
	}
	avatarDir := filepath.Join(s.cfg.UploadsDir, "avatars")
	if err := os.MkdirAll(avatarDir, 0o755); err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	avatarPath := filepath.Join(avatarDir, itoa(user.ID)+".jpg")
	if err := os.WriteFile(avatarPath, data, 0o644); err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	avatarURL := "/uploads/avatars/" + itoa(user.ID) + ".jpg"
	if _, err := s.store.UpdateUser(user.Username, func(u *store.User) { u.AvatarURL = avatarURL }); err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, avatarResponse{AvatarURL: avatarURL})
}

func (s *Server) handleStats(w http.ResponseWriter, r *http.Request) {
	user, ok := s.extractUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	imageTs := s.store.UsageTimestamps(user.Username, "image")
	videoTs := s.store.UsageTimestamps(user.Username, "video")
	writeJSON(w, http.StatusOK, userStats{
		Image: periodCountsOf(imageTs),
		Video: periodCountsOf(videoTs),
	})
}

// periodCountsOf 按 UTC 聚合今日 / 本周 / 本月 / 累计计数，与 Python 版一致。
func periodCountsOf(timestamps []int64) periodCounts {
	now := time.Now().UTC()
	todayStart := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC)
	weekStart := todayStart.AddDate(0, 0, -int(now.Weekday()))
	monthStart := time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC)

	var today, week, month int
	for _, ts := range timestamps {
		dt := time.UnixMilli(ts)
		if !dt.Before(todayStart) {
			today++
		}
		if !dt.Before(weekStart) {
			week++
		}
		if !dt.Before(monthStart) {
			month++
		}
	}
	return periodCounts{Today: today, Week: week, Month: month, Total: len(timestamps)}
}
