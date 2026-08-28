# Agnes AI Chat 认证服务

FastAPI 注册/登录服务，部署于用户自有云服务器，为安卓 App 提供账号注册与登录接口。

## 测试部署（推荐，零依赖）

测试环境不需要安装任何依赖，直接运行单文件版即可（Python 3.8+，标准库实现）：

```bash
python3 simple_server.py
```

- 默认监听 `0.0.0.0:8000`，可用 `--port` 指定端口
- 用户数据存 JSON 文件 `users.json`（与脚本同目录），可用 `--data` 指定路径
- 无需配置环境变量、无需数据库，启动即用
- 正式环境请使用下方的 FastAPI 版本

## 正式部署（FastAPI）

```bash
pip install -r requirements.txt
export JWT_SECRET='<长随机字符串>'
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 测试

```bash
python -m pytest tests/ -v
```

## 接口

以下接口两个版本均提供，且与安卓端 `ServerApiService` 完全兼容：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/register | 注册（201；重复账号 409；格式非法 400） |
| POST | /api/v1/auth/login | 登录（200 + token；凭据错误 401） |
| POST | /api/v1/auth/logout | 登出（占位） |
| GET | /api/v1/announcements/latest | 最新公告 |
| GET | /api/v1/app/version | 版本与更新信息 |
| POST | /api/v1/generation/image | AI 图片生成（单文件版返回 mock） |
| POST | /api/v1/generation/video | AI 视频生成（单文件版返回 mock） |
| GET | /health | 健康检查 |

## 配置（环境变量，仅 FastAPI 正式版需要）

- `JWT_SECRET`：JWT 签名密钥，生产必须设置为长随机字符串
- `TOKEN_EXPIRE_MINUTES`：token 有效期，默认 43200（30 天）
- `DATABASE_URL`：数据库连接串，默认 `sqlite:///./app.db`，生产可切换 PostgreSQL

> 单文件测试版 `simple_server.py` 不需要任何环境变量；密码使用 PBKDF2-SHA256 加盐哈希，用户数据存 JSON 文件。

## 登录响应示例

```json
{
  "token": "<token>",
  "user": { "id": "1", "username": "alice", "nickname": "alice" }
}
```

> 注意：`user.id` 返回字符串、始终包含 `nickname` 字段，与安卓端 `UserDto` 的 Kotlin 类型定义保持一致。
