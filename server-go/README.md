# Agnes AI Chat Go 后端

与安卓端 `ServerApiService` 完全兼容的 Go 版业务服务器，替换原 Python（FastAPI / simple_server）实现。零第三方依赖，仅用标准库。

## 特性

- 注册 / 登录（JWT HS256 鉴权，30 天默认有效期）
- 公告、版本与更新信息
- 个人资料修改、改密、base64 头像上传
- 生成统计（今日 / 本周 / 本月 / 累计，UTC 聚合）
- AI 图片 / 视频生成 mock（记账）
- 用户与用量数据 JSON 文件持久化（原子写入）

## 接口

与安卓端 `ServerApiService` 完全一致：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/register | 注册（201；重复账号 409；格式非法 400） |
| POST | /api/v1/auth/login | 登录（200 + token；凭据错误 401） |
| POST | /api/v1/auth/logout | 登出 |
| GET | /api/v1/announcements/latest | 最新公告 |
| GET | /api/v1/app/version | 版本与更新信息 |
| POST | /api/v1/generation/image | AI 图片生成（mock） |
| POST | /api/v1/generation/video | AI 视频生成（mock） |
| PUT | /api/v1/user/profile | 修改昵称 |
| POST | /api/v1/user/password | 修改密码 |
| POST | /api/v1/user/avatar | 上传头像（base64） |
| GET | /api/v1/user/stats | 生成统计 |
| GET | /health | 健康检查 |
| GET | /uploads/... | 头像静态文件 |

响应字段与 Python 版保持一致：`user.id` 为字符串、始终包含 `nickname`，错误为 `{"detail": "..."}`。

## 快速开始

```bash
# 直接运行（默认端口 8000，数据在 data/，上传在 uploads/）
cd server-go
go run .

# 或构建后运行
make build
./bin/agnes-server
```

## 配置（环境变量）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| PORT | 8000 | 监听端口 |
| JWT_SECRET | change-me-in-production | JWT 签名密钥，生产必须设置 |
| TOKEN_EXPIRE_MINUTES | 43200 | token 有效期（30 天） |
| USERS_FILE | data/users.json | 用户数据文件 |
| USAGE_FILE | data/usage.json | 生成用量文件 |
| UPLOADS_DIR | uploads | 头像上传目录 |

可通过 `.env.example` 复制为环境变量使用（示例：`export $(grep -v '^#' .env.example | xargs)`）。

## 测试

```bash
make test        # go test ./... -v
make vet         # go vet ./...
```

## Docker 编排

```bash
# 构建并启动（端口 8000，数据挂载到 ./data 与 ./uploads）
docker compose up -d --build

# 健康检查
curl http://localhost:8000/health
```

## 目录结构

```
server-go/
├── main.go                  # 入口：配置加载、存储初始化、启动 HTTP
├── internal/
│   ├── config/              # 环境变量配置
│   ├── security/            # PBKDF2-SHA256 密码哈希 + HS256 JWT
│   ├── store/               # JSON 文件持久化（用户 / 用量）
│   └── api/                 # 路由、处理器、鉴权、CORS
├── Makefile
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

## 与安卓 App 对接

App 的 `BuildConfig.SERVER_BASE_URL` 默认指向 `http://your-server:8000/`。构建时覆盖为 Go 服务器地址：

```bash
cd ..
./gradlew assembleDebug -PSERVER_BASE_URL=http://your-host:8000/
```
