<div align="center">

# Agnes AI Chat

基于 [Agnes 2.5 Flash](https://www.agnes-ai.cn/zh-Hans/docs/agnes-25-flash) 大模型的安卓 AI 聊天创作应用

Kotlin · Jetpack Compose · Material Design 3 · 纯本地运行

[![Release](https://img.shields.io/github/v/release/198748052/agnes-ai-chat)](https://github.com/198748052/agnes-ai-chat/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

纯本地运行的 AI 聊天与创作助手：流式对话、AI 图片 / 视频生成、作品管理，全部数据保存在你的手机上，无需注册登录，只需一个 Agnes API Key。

## 下载安装

前往 [Releases](https://github.com/198748052/agnes-ai-chat/releases) 下载最新 APK（约 12 MB）：

1. 在手机文件管理器中打开 APK
2. 允许「安装未知来源应用」
3. 打开 App，「我的」→「设置」填入 Agnes API Key（在 [agnes-ai.cn](https://www.agnes-ai.cn) 获取）
4. 返回聊天界面，开始对话

系统要求：Android 8.0+（API 26）

## 功能特性

**对话**
- 实时流式输出（SSE 增量渲染）
- 多轮上下文对话
- 聊天历史本地持久化（Room），重启自动恢复
- 侧滑抽屉新建 / 切换会话
- 自定义系统提示词，控制助手角色与风格
- 内置博客页（WebView）

**AI 创作**
- 文字 / 图片 / 视频三种生成模式
- 图片 / 视频均支持新一代 Agnes 2.5 Flash 模型：图片更高清、提示词遵循更强；视频支持 4-12 秒时长
- 图片生成支持参考图上传，视频生成支持首尾帧上传
- 生成参数可视化配置并随会话记忆
- 生成任务后台轮询：生成中可新建对话，完成后结果回到原会话

**作品与数据**
- 我的作品：集中浏览已生成的图片与视频，大图预览、视频播放、保存相册、分享、删除、跳转原会话
- 创作统计
- 存储空间管理：分类统计占用，支持分类清理与一键清理

**体验**
- Material Design 3 动态取色（Android 12+）
- 错误容错：网络异常、HTTP 401/429、超时均有明确提示

## 从源码构建

环境要求：

- JDK 17
- Android SDK（compileSdk 34，build-tools 34.0.0，minSdk 26）
- Gradle 8.7（项目自带 wrapper）

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（默认使用 debug 签名，可直接安装）
./gradlew assembleRelease
```

产物输出到 `app/build/outputs/apk/<debug|release>/`。

## 项目结构

```
app/src/main/java/com/agnesai/chat/
├── data/
│   ├── network/          # Retrofit + OkHttp SSE 流式解析；Agnes 对话 / 生成接口与 DTO
│   ├── local/            # Room 数据库 + DataStore（聊天历史 / 设置）
│   ├── repository/       # ChatRepository（流式对话核心逻辑）
│   ├── generation/       # 图片 / 视频生成仓库（任务创建与后台轮询）
│   ├── works/            # 我的作品聚合查询
│   ├── stats/            # 创作统计
│   └── storage/          # 存储空间统计与清理
├── di/                   # AppContainer 手动依赖注入
├── ui/
│   ├── chat/             # 聊天界面与会话管理
│   ├── conversation/     # 会话切换抽屉
│   ├── generation/       # AI 创作窗口（文字 / 图片 / 视频）
│   ├── blog/             # 博客页（WebView）
│   ├── myworks/          # 我的作品
│   ├── stats/            # 创作统计
│   ├── storage/          # 存储空间管理
│   ├── profile/          # 个人中心
│   ├── settings/         # 设置页
│   ├── common/           # 共享组件（媒体存取、视频播放器、作品操作）
│   └── theme/            # Material3 主题
├── AppNavHost.kt         # 导航（底部 Tab：聊天 / 博客 / 我的）
└── AppRoot.kt            # 应用根节点
```

架构：MVVM + 手动依赖注入，数据层仓库模式，UI 层 Compose 声明式界面。

## API 配置

App 通过 OpenAI 兼容接口调用 Agnes 大模型：

| 参数 | 值 |
|------|-----|
| Base URL | `https://api.agnes-ai.cn/` |
| Endpoint | `POST /v1/chat/completions` |
| Model | `agnes-2.5-flash` |
| 鉴权 | `Authorization: Bearer <API_KEY>` |
| 流式 | `stream: true` |

API Key 在 App「设置」页配置，仅保存在本地 DataStore，不上传任何服务器。

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖：`StreamParser` SSE 解析、`ChatRepository` / `ChatViewModel`（含错误与取消路径）、生成参数编解码与视频尺寸解析、`MyWorksRepository` / `MyWorksViewModel`、`StorageModels`。

## 许可证

[MIT](LICENSE)
