package api

import "encoding/base64"

func base64EncodedImage() string {
	return base64.StdEncoding.EncodeToString([]byte("fake-jpeg-bytes"))
}
