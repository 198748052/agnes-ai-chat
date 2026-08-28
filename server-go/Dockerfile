FROM golang:1.25-alpine AS builder

WORKDIR /src
COPY go.mod ./
COPY . .

RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o /out/agnes-server .

FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app
COPY --from=builder /out/agnes-server /app/agnes-server

ENV PORT=8000 \
    JWT_SECRET=change-me-in-production \
    TOKEN_EXPIRE_MINUTES=43200 \
    USERS_FILE=data/users.json \
    USAGE_FILE=data/usage.json \
    UPLOADS_DIR=uploads

EXPOSE 8000

CMD ["/app/agnes-server"]
