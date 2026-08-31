# Agnes AI Chat

一款基于 [Agnes 2.5 Flash](https://www.agnes-ai.cn/zh-Hans/docs/agnes-25-flash) 大模型的安卓聊天创作 App。

- 技术栈：Kotlin + Jetpack Compose (Material Design 3)
- 模型：`agnes-2.5-flash`（OpenAI 兼容 Chat Completions 接口）
- 特性：流式输出、多轮对话、本地持久化、AI 图片 / 视频生成，纯本地运行

## 功能

- 实时流式对话（SSE 增量渲染）
- 多轮上下文对话
- 聊天历史本地持久化（Room 数据库），重启后自动恢复
- 聊天记录切换（侧滑抽屉新建 / 切换会话）
- AI 创作对话窗口（文字 / 图片 / 视频生成模式切换）
- 图片生成支持参考图上传，视频生成支持首尾帧上传
- 生成任务后台轮询：生成中新建对话不再被阻塞，完成后结果回到原会话
- 我的作品：集中浏览 / 筛选全部已生成的图片与视频作品，支持大图预览、视频播放、保存相册、分享、删除与跳转原会话
- 存储空间：统计本地数据占用（会话 / 图片 / 缓存），支持分类清理与清理全部
- 博客页：内置 WebView 浏览博客
- App 内配置 API Key（DataStore）
- 自定义系统提示词，控制助手角色与风格
- 错误容错：网络异常、HTTP 401/429、超时均有明确提示
- Material Design 3 动态取色（Android 12+）

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
2. 打开 App 即可直接使用，无需注册登录
3. 进入「我的」页 →「设置」，填入你的 Agnes API Key（在 [agnes-ai.cn](https://www.agnes-ai.cn) 获取）
4. （可选）自定义系统提示词
5. 返回聊天界面，开始对话

## 项目结构

```
app/src/main/java/com/agnesai/chat/
├── data/
│   ├── network/          # Retrofit + OkHttp SSE 流式解析；AgnesApiService / AgnesGenerationApiService 接口与 DTO
│   ├── local/            # Room 数据库 + DataStore（聊天历史 / 设置 / 作品元数据）
│   ├── generation/       # AI 图片 / 视频生成仓库（Agnes 官方接口）
│   ├── works/            # 我的作品仓库（聚合查询已完成生成消息）
│   ├── stats/            # 创作统计仓库
│   ├── storage/          # 存储空间统计与清理仓库
│   └── repository/       # ChatRepository 数据仓库
├── di/                   # AppContainer 手动依赖注入
├── ui/
│   ├── chat/             # 聊天界面（支持会话切换）与 ChatViewModel
│   ├── conversation/     # 聊天记录抽屉
│   ├── generation/       # AI 创作窗口（文字 / 图片 / 视频）
│   ├── blog/             # 博客页（WebView）
│   ├── myworks/          # 我的作品列表与详情（大图 / 视频 / 保存 / 分享 / 删除）
│   ├── stats/            # 创作统计
│   ├── storage/          # 存储空间统计与清理
│   ├── profile/          # 个人中心
│   ├── settings/         # 设置界面与 SettingsViewModel
│   ├── common/           # 共享组件（媒体存取 AppMediaStorage、视频播放器、作品操作）
│   ├── theme/            # Material3 主题
│   ├── AppRoot.kt        # 应用根节点
│   └── AppNavHost.kt     # 导航（底部 Tab：聊天 / 博客 / 我的）
├── AgnesChatApplication.kt
└── MainActivity.kt
```

## 配置说明

| 参数 | 值 |
|------|-----|
| Base URL | `https://api.agnes-ai.cn/` |
| Endpoint | `POST /v1/chat/completions` |
| Model | `agnes-2.5-flash` |
| 鉴权 | `Authorization: Bearer <API_KEY>` |
| 流式 | `stream: true` |

API Key 在 App「设置」页配置，仅保存在本地 DataStore 中。

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖：`StreamParser` SSE 解析、`ChatRepository` / `ChatViewModel`（含错误与取消路径）、生成参数编解码与视频尺寸解析、`MyWorksRepository` / `MyWorksViewModel`、`StorageModels`。

## 后端服务（可选）

`server-go/` 与 `server/` 是早期版本（含账号体系时）的配套后端，当前 App 已完全本地化，无需部署即可使用全部功能。如需参考：

- **Go 版（推荐）**：`server-go/`，零第三方依赖（纯标准库），支持 Docker Compose / systemd 编排，详见 `server-go/README.md`
- **Python 单文件测试版**：`python3 server/simple_server.py`（标准库实现，用户数据存 JSON），详见 `server/README.md`

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。
