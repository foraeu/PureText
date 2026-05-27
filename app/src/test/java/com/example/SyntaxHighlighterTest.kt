package com.example

import com.example.utils.HighlightTheme
import com.example.utils.OutlineParser
import com.example.utils.SyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun testDetectLanguageForR() {
        assertEquals("r", SyntaxHighlighter.detectLanguage("script.R"))
        assertEquals("r", SyntaxHighlighter.detectLanguage("analysis.r"))
        assertEquals("r", SyntaxHighlighter.detectLanguage("doc.rmd"))
    }

    @Test
    fun testRSyntaxHighlighting() {
        val rCode = "my_var <- 42L # This is a comment\nmy_func <- function(x) { print(x) }"
        val theme = HighlightTheme.SOFT_DARK
        val highlighted = SyntaxHighlighter.highlight(rCode, "r", theme)
        
        // Ensure comments are detected
        val text = highlighted.text
        assertTrue(text.contains("This is a comment"))
    }

    @Test
    fun testROutlineParser() {
        val lines = listOf(
            "my_func <- function(x) {",
            "  print(x)",
            "}",
            "another_func = function() {",
            "  return(42)",
            "}"
        )
        val symbols = OutlineParser.parseSymbols(lines, "r")
        assertEquals(2, symbols.size)
        assertEquals("my_func()", symbols[0].label)
        assertEquals("another_func()", symbols[1].label)
    }

    @Test
    fun testEpubOutlineParser() {
        val lines = listOf(
            "卷一 五帝本纪第一",
            "一些文本内容",
            "第一章 远古传说",
            "第二章 夏商周"
        )
        val symbols = OutlineParser.parseSymbols(lines, "epub")
        assertEquals(3, symbols.size)
        assertEquals("卷一 五帝本纪第一", symbols[0].label)
        assertEquals("第一章 远古传说", symbols[1].label)
        assertEquals("第二章 夏商周", symbols[2].label)
    }
}
