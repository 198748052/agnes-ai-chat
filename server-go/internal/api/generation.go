package api

import "net/http"

type generationRequest struct {
	Prompt string `json:"prompt"`
	Mode   string `json:"mode"`
}

type generationResponse struct {
	TaskID   string  `json:"task_id"`
	MediaURL *string `json:"media_url"`
}

func (s *Server) handleGeneration(w http.ResponseWriter, r *http.Request, genType string) {
	user, ok := s.extractUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	var req generationRequest
	if !decodeBody(w, r, &req) {
		return
	}
	if req.Prompt == "" {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	if err := s.store.AddUsage(user.Username, genType); err != nil {
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, generationResponse{TaskID: "mock-task", MediaURL: nil})
}
