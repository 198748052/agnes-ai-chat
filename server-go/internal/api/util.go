package api

import (
	"encoding/json"
	"strconv"
)

func itoa(n int) string {
	return strconv.Itoa(n)
}

func jsonUnmarshal(data []byte, dst any) error {
	return json.Unmarshal(data, dst)
}
