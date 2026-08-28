package api

import "net/http"

type announcementOut struct {
	ID        string `json:"id"`
	Title     string `json:"title"`
	Content   string `json:"content"`
	Priority  string `json:"priority"`
	PublishAt int64  `json:"publish_at"`
}

type versionOut struct {
	LatestVersionCode int    `json:"latest_version_code"`
	LatestVersionName string `json:"latest_version_name"`
	ForceUpdate       bool   `json:"force_update"`
	UpdateLog         string `json:"update_log"`
	DownloadURL       string `json:"download_url"`
}

var announcements = []announcementOut{
	{
		ID:        "welcome",
		Title:     "欢迎使用 Agnes AI Chat",
		Content:   "Agnes AI Chat 是一款基于 Agnes 2.5 Flash 大模型的安卓聊天应用，支持实时流式对话、多轮上下文、聊天记录本地持久化，以及 AI 图片 / 视频创作。",
		Priority:  "important",
		PublishAt: 0,
	},
	{
		ID:        "api-key",
		Title:     "使用前请先配置 API Key",
		Content:   "首次使用请在「我的」-「设置」中填入你的 Agnes API Key，并根据需要自定义系统提示词。",
		Priority:  "normal",
		PublishAt: 0,
	},
}

var versionInfo = versionOut{
	LatestVersionCode: 1,
	LatestVersionName: "1.0",
	ForceUpdate:       false,
	UpdateLog:         "",
	DownloadURL:       "",
}

func (s *Server) handleAnnouncements(w http.ResponseWriter, r *http.Request) {
	if len(announcements) == 0 {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("null"))
		return
	}
	writeJSON(w, http.StatusOK, announcements[0])
}

func (s *Server) handleVersion(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, versionInfo)
}
