package store

import (
	"time"
)

func timeNowUTC() string {
	return time.Now().UTC().Format(time.RFC3339)
}

func timeNowUnixMS() int64 {
	return time.Now().UTC().UnixMilli()
}
