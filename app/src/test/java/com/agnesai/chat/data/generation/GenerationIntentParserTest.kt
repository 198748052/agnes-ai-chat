package com.agnesai.chat.data.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationIntentParserTest {

    @Test
    fun `parses image intent marker`() {
        val intent = GenerationIntentParser.parse("好的，我来为你生成：[GENERATE_IMAGE]一只可爱的橘猫[/GENERATE_IMAGE]")
        assertEquals(GenerationIntent.Image("一只可爱的橘猫"), intent)
    }

    @Test
    fun `parses video intent marker`() {
        val intent = GenerationIntentParser.parse(
            "收到，开始生成视频：[GENERATE_VIDEO]夕阳下的海边，海浪轻拍沙滩[/GENERATE_VIDEO]"
        )
        assertEquals(GenerationIntent.Video("夕阳下的海边，海浪轻拍沙滩"), intent)
    }

    @Test
    fun `plain reply has no intent`() {
        assertNull(GenerationIntentParser.parse("你好！今天想聊点什么？"))
    }

    @Test
    fun `empty prompt is treated as no intent`() {
        assertNull(GenerationIntentParser.parse("[GENERATE_IMAGE]   [/GENERATE_IMAGE]"))
    }

    @Test
    fun `unclosed marker is treated as no intent`() {
        assertNull(GenerationIntentParser.parse("我来生成：[GENERATE_IMAGE]一只猫"))
        assertNull(GenerationIntentParser.parse("[/GENERATE_IMAGE]一只猫"))
    }

    @Test
    fun `image marker takes priority when both present`() {
        val reply = "[GENERATE_IMAGE]猫[/GENERATE_IMAGE] 和 [GENERATE_VIDEO]狗[/GENERATE_VIDEO]"
        assertEquals(GenerationIntent.Image("猫"), GenerationIntentParser.parse(reply))
    }

    @Test
    fun `prompt is trimmed`() {
        val intent = GenerationIntentParser.parse("[GENERATE_IMAGE]  一只猫  [/GENERATE_IMAGE]")
        assertEquals(GenerationIntent.Image("一只猫"), intent)
    }

    @Test
    fun `display text strips paired markers`() {
        val reply = "好的，正在为你生成图片：\n[GENERATE_IMAGE]一只可爱的橘猫[/GENERATE_IMAGE]"
        assertEquals("好的，正在为你生成图片：", GenerationIntentParser.displayText(reply))
    }

    @Test
    fun `display text strips orphan tags`() {
        // 畸形标记（缺闭合）兜底策略：移除标签字面量，保留剩余文本
        assertEquals("好的一只猫", GenerationIntentParser.displayText("好的[GENERATE_IMAGE]一只猫"))
        assertEquals("一只猫", GenerationIntentParser.displayText("[/GENERATE_VIDEO]一只猫"))
    }

    @Test
    fun `display text of plain reply is unchanged`() {
        assertEquals("普通回复", GenerationIntentParser.displayText("普通回复"))
    }
}
