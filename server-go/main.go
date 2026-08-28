package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"

	"agneschat/server/internal/api"
	"agneschat/server/internal/config"
	"agneschat/server/internal/store"
)

func main() {
	cfg := config.Load()

	for _, dir := range []string{cfg.UploadsDir, filepath.Dir(cfg.UsersFile), filepath.Dir(cfg.UsageFile)} {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			log.Fatalf("create dir %s: %v", dir, err)
		}
	}

	st, err := store.New(cfg.UsersFile, cfg.UsageFile)
	if err != nil {
		log.Fatalf("init store: %v", err)
	}

	srv := api.New(cfg, st)

	addr := ":" + cfg.Port
	log.Printf("Agnes AI Chat Go server listening on %s", addr)
	log.Printf("users file: %s, usage file: %s", cfg.UsersFile, cfg.UsageFile)
	if err := http.ListenAndServe(addr, srv.Handler()); err != nil {
		log.Fatal(err)
	}
}
