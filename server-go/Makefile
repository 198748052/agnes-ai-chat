BINARY=agnes-server
PKG=./...
LDFLAGS=-s -w

.PHONY: build run test vet fmt clean

build:
	go build -ldflags "$(LDFLAGS)" -o bin/$(BINARY) .

run:
	go run .

test:
	go test $(PKG) -v

vet:
	go vet $(PKG)

fmt:
	gofmt -w .

clean:
	rm -rf bin
