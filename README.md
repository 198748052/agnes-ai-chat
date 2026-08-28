# Agnes AI Chat

一款基于 [Agnes 2.5 Flash](https://www.agnes-ai.cn/zh-Hans/docs/agnes-25-flash) 大模型的安卓聊天创作 App。

- 技术栈：Kotlin + Jetpack Compose (Material Design 3)
- 模型：`agnes-2.5-flash`（OpenAI 兼容 Chat Completions 接口）
- 特性：流式输出、多轮对话、本地持久化、API Key 与系统提示词自定义
- 框架：账号注册 / 登录、公告弹窗、更新推送 / 强制更新、AI 图片 / 视频生成对话、我的作品、存储空间管理

## 功能

- 实时流式对话（SSE 增量渲染）
- 多轮上下文对话
- 聊天历史本地持久化（Room 数据库），重启后自动恢复
- 聊天记录切换（侧滑抽屉新建 / 切换会话）
- 账号注册 / 登录（登录门禁，登录态本地持久化，JWT 鉴权）
- 公告弹窗（已读记忆，登录后自动拉取展示）
- 更新推送与强制更新机制（版本对比 + 弹窗）
- AI 创作对话窗口（文字 / 图片 / 视频生成模式切换）
- 我的作品：集中浏览 / 筛选全部已生成的图片与视频作品，支持大图预览、视频播放、保存相册、分享、删除与跳转原会话
- 存储空间：统计本地数据占用（会话 / 图片 / 缓存），支持分类清理与清理全部
- 个人中心：我的作品、存储空间、设置、帮助与反馈、关于、退出登录
- App 内配置 API Key（DataStore）
- 自定义系统提示词，控制助手角色与风格
- 错误容错：网络异常、HTTP 401/429、超时均有明确提示
- Material Design 3 动态取色（Android 12+）

> 登录、公告、更新、AI 生成等模块均已通过 `ServerApiService` 真实对接业务服务器（默认 `http://your-server:8000`，可通过 Gradle 属性 `SERVER_BASE_URL` 覆盖）。

## 环境要求

- JDK 17
- Android SDK (compileSdk 34, build-tools 34.0.0, minSdk 26)
- Gradle 8.7（使用项目自带 wrapper）

## 构建

```bash
# 本地构建 Debug APK
./gradlew assembleDebug
```

产物输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 安装使用

1. 构建或下载 APK 后安装到 Android 8.0+ 设备
2. 注册并登录账号（或使用已有账号）
3. 进入「我的」页 →「设置」，填入你的 Agnes API Key（在 [agnes-ai.cn](https://www.agnes-ai.cn) 获取）
4. （可选）自定义系统提示词
5. 返回聊天界面，开始对话

## 项目结构

```
app/src/main/java/com/agnesai/chat/
├── data/
│   ├── network/          # Retrofit + OkHttp SSE 流式解析；AgnesApiService / AgnesGenerationApiService / ServerApiService 接口与 DTO
│   ├── local/            # Room 数据库 + DataStore（设置 / 登录态 / 已读公告）
│   ├── auth/             # 账号注册 / 登录仓库（对接 ServerApiService，JWT）
│   ├── announcement/     # 公告仓库
│   ├── update/           # 更新检查仓库
│   ├── generation/       # AI 图片 / 视频生成仓库（Agnes 官方接口）
│   ├── works/            # 我的作品仓库（聚合查询已完成生成消息）
│   ├── storage/          # 存储空间统计与清理仓库
│   └── repository/       # ChatRepository 数据仓库
├── di/                   # AppContainer 手动依赖注入
├── ui/
│   ├── auth/             # 登录 / 注册页（登录门禁）
│   ├── chat/             # 聊天界面（支持会话切换）与 ChatViewModel
│   ├── conversation/     # 聊天记录抽屉
│   ├── generation/       # AI 创作窗口（文字 / 图片 / 视频）
│   ├── announcement/     # 公告弹窗
│   ├── update/           # 更新弹窗（支持强制更新）
│   ├── myworks/          # 我的作品列表与详情（大图 / 视频 / 保存 / 分享 / 删除）
│   ├── storage/          # 存储空间统计与清理
│   ├── profile/          # 个人中心
│   ├── settings/         # 设置界面与 SettingsViewModel
│   ├── common/           # 共享组件（媒体存取 AppMediaStorage、视频播放器、作品操作）
│   ├── theme/            # Material3 主题
│   ├── AppRoot.kt        # 应用根节点（登录门禁 + 全局更新弹窗）
│   └── AppNavHost.kt     # 导航（底部 Tab：聊天 / 公告 / 我的）
├── AgnesChatApplication.kt
└── MainActivity.kt
```

## 服务器对接说明

业务服务器与 Agnes 官方接口分离：

- **Agnes 官方接口**：`https://api.agnes-ai.cn/`（对话、图片 / 视频生成），API Key 在 App 设置内配置
- **业务服务器**：`BuildConfig.SERVER_BASE_URL`（默认 `http://your-server:8000/`），通过 `ServerApiService` 提供注册 / 登录、公告、更新检查等接口，`AuthInterceptor` 自动附加 JWT，401 时清除本地登录态
- 修改业务服务器地址：`./gradlew assembleDebug -PSERVER_BASE_URL=http://your-host:port/`

## 配置说明

| 参数 | 值 |
|------|-----|
| Base URL | `https://api.agnes-ai.cn/` |
| Endpoint | `POST /v1/chat/completions` |
| Model | `agnes-2.5-flash` |
| 鉴权 | `Authorization: Bearer <API_KEY>` |
| 流式 | `stream: true` |

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖：`StreamParser` SSE 解析、`AuthInterceptor`、`ChatRepository` / `ChatViewModel`（含错误与取消路径）、`AuthRepositoryImpl` / `AuthViewModel`、生成参数编解码与视频尺寸解析、`MyWorksRepository` / `MyWorksViewModel`、`StorageModels`。

## 后端服务

- **Go 正式版（推荐）**：`server-go/`，零第三方依赖（纯标准库），接口与安卓端 `ServerApiService` 完全兼容，支持 Docker Compose / systemd 编排，详见 `server-go/README.md`
- 单文件零依赖测试版：`python3 server/simple_server.py`（Python 标准库，用户数据存 JSON），详见 `server/README.md`
- 正式版（旧）：`server/` 下 FastAPI 应用，详见 `server/README.md`
