package com.agnesai.chat.data.generation

/**
 * AI 生成模式：
 * - [TEXT]  文字对话（后续可复用主聊天接口）
 * - [IMAGE] 文生图
 * - [VIDEO] 文生视频
 */
enum class GenerationMode {
    TEXT,
    IMAGE,
    VIDEO
}
