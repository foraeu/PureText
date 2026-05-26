package com.example.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

enum class HighlightTheme(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val accent: Color,
    val comment: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val typeOrBuiltIn: Color
) {
    SOFT_DARK(
        background = Color(0xFF121417),
        surface = Color(0xFF1A1D24),
        textPrimary = Color(0xFFE2E8F0),
        accent = Color(0xFF818CF8), // Indigo
        comment = Color(0xFF64748B), // Slate grey
        keyword = Color(0xFFF472B6), // Pink
        string = Color(0xFF34D399), // Emerald
        number = Color(0xFFF59E0B), // Amber
        typeOrBuiltIn = Color(0xFF60A5FA) // Sky Blue
    ),
    DEEP_BLACK(
        background = Color(0xFF000000),
        surface = Color(0xFF111111),
        textPrimary = Color(0xFFF3F4F6),
        accent = Color(0xFF38BDF8), // Light Blue
        comment = Color(0xFF525252), // Muted deep gray
        keyword = Color(0xFFF43F5E), // Rose red
        string = Color(0xFF10B981), // Green
        number = Color(0xFFF59E0B), // Amber
        typeOrBuiltIn = Color(0xFF0EA5E9) // Deep Sky Blue
    ),
    SEPIA(
        background = Color(0xFFFBF4E6),
        surface = Color(0xFFF5EADA),
        textPrimary = Color(0xFF433422), // Low ear fatigue deep brown
        accent = Color(0xFFD97706), // Warm orange amber
        comment = Color(0xFF7C6E5E), // Soft earthy grey
        keyword = Color(0xFFB91C1C), // Deep crimson
        string = Color(0xFF15803D), // Soft forest green
        number = Color(0xFF0369A1), // Dull blue
        typeOrBuiltIn = Color(0xFF7C3AED) // Muted purple
    ),
    PAPER_LIGHT(
        background = Color(0xFFFDF8F6), // Warm Parchment/Bento base
        surface = Color(0xFFF3EDF7), // Bento light-purple grey panel surface
        textPrimary = Color(0xFF1D1B20), // Bento dark carbon text
        accent = Color(0xFF6750A4), // M3 Primary Purple
        comment = Color(0xFF6A737D), // Soft muted grey
        keyword = Color(0xFFAF52BF), // Purple/Magenta keyword
        string = Color(0xFF008021), // Standard forest green
        number = Color(0xFF005CC5), // Soft deep blue
        typeOrBuiltIn = Color(0xFFD01818) // Red types/built-ins
    ),
    HIGH_CONTRAST(
        background = Color(0xFF050510),
        surface = Color(0xFF101026),
        textPrimary = Color(0xFFFFFFFF),
        accent = Color(0xFF00FFCC), // Neon teal
        comment = Color(0xFF667799), // Cyan-grey
        keyword = Color(0xFFFF007F), // Glowing magenta
        string = Color(0xFF00FF66), // Glowing green
        number = Color(0xFFFFCC00), // Glowing yellow
        typeOrBuiltIn = Color(0xFF00CCFF) // Glowing blue
    );

    companion object {
        fun fromName(name: String): HighlightTheme {
            return when (name) {
                "Deep Black" -> DEEP_BLACK
                "Sepia" -> SEPIA
                "Paper Light" -> PAPER_LIGHT
                "High Contrast" -> HIGH_CONTRAST
                else -> SOFT_DARK
            }
        }
    }
}

object SyntaxHighlighter {

    // Cache compiled patterns for languages as individual Regex components
    private val regexListCache = mutableMapOf<String, List<Pair<String, Regex>>>()

    fun detectLanguage(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "py", "pyw" -> "python"
            "rs" -> "rust"
            "ts", "tsx" -> "typescript"
            "js", "jsx" -> "javascript"
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "json" -> "json"
            "md", "markdown" -> "markdown"
            "xml", "html", "xhtml" -> "markup"
            "sh", "bash", "zsh" -> "bash"
            "css" -> "css"
            "epub" -> "epub"
            else -> "text"
        }
    }

    private fun getPatternsForLanguage(lang: String): List<Pair<String, String>> {
        val commentP = "//.*|/\\*[\\s\\S]*?\\*/"
        val stringP = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
        val numberP = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b"

        return when (lang) {
            "kotlin", "java" -> listOf(
                "COMMENT" to "//.*" , // Single line comments
                "STRING" to stringP,
                "KEYWORD" to "\\b(fun|class|interface|object|val|var|package|import|return|if|else|when|for|while|do|break|continue|try|catch|finally|throw|this|super|as|is|in|companion|init|constructor|suspend|inline|sealed|data|private|protected|public|internal|override|void|int|double|float|long|short|byte|boolean|char|static|final|extends|implements|new|throws)\\b",
                "NUMBER" to "\\b\\d+(?:\\.\\d+)?(?:[fFLuU])?\\b",
                "BUILTIN" to "@[a-zA-Z_]\\w*|\\b(String|Int|Long|Float|Double|Boolean|Char|Byte|Short|Any|Unit|Nothing|List|Set|Map|HashMap|ArrayList|System|out|println|print)\\b"
            )
            "python" -> listOf(
                "COMMENT" to "#.*",
                "STRING" to "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",
                "KEYWORD" to "\\b(def|class|import|from|as|if|elif|else|while|for|in|return|try|except|finally|with|lambda|pass|break|continue|and|or|not|is|None|True|False|assert|global|nonlocal|del)\\b",
                "NUMBER" to numberP,
                "BUILTIN" to "@[a-zA-Z_]\\w*|\\b(print|len|range|str|int|float|bool|list|dict|set|tuple|enumerate|zip|sum|min|max|abs|open)\\b"
            )
            "rust" -> listOf(
                "COMMENT" to "//.*",
                "STRING" to "\"(?:\\\\.|[^\"\\\\])*\"|'[^']'",
                "KEYWORD" to "\\b(fn|struct|enum|impl|trait|mod|use|pub|let|mut|match|if|else|for|while|loop|return|type|where|crate|self|Self|const|static|unsafe|async|await|dyn|as|in|move|ref|use)\\b",
                "NUMBER" to "\\b\\d+(?:\\.\\d+)?(?:[a-zA-Z0-9_]+)?\\b",
                "BUILTIN" to "\\b(String|Option|Result|Some|None|Ok|Err|Vec|Box|Rc|Arc|println|print|format|panic|i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize|f32|f64|bool|char)\\b|#[^\\]]+\\]"
            )
            "typescript", "javascript" -> listOf(
                "COMMENT" to "//.*",
                "STRING" to "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`",
                "KEYWORD" to "\\b(const|let|var|function|class|import|export|from|as|if|else|for|while|do|switch|case|default|break|continue|return|try|catch|finally|throw|new|this|typeof|instanceof|async|await|yield|null|undefined|true|false|interface|type|extends|implements|namespace|public|private|protected|readonly|any|string|number|boolean|unknown|never|void)\\b",
                "NUMBER" to numberP,
                "BUILTIN" to "\\b(console|log|error|warn|info|window|document|process|global|require|module|exports|JSON|stringify|parse|Math|Promise|Set|Map|Array|Object|String|Number|Boolean)\\b"
            )
            "json" -> listOf(
                "KEYWORD" to "\"[^\"\\\\]*\"(?=\\s*:)", // Keys
                "STRING" to "(?<=:\\s*)\"(?:\\\\.|[^\"\\\\])*\"", // Value strings
                "NUMBER" to "-?\\b\\d+(?:\\.\\d+)?\\b",
                "BUILTIN" to "\\b(true|false|null)\\b"
            )
            "markdown" -> listOf(
                "KEYWORD" to "^#{1,6}\\s+.*$|^(?:\\*|\\-|\\+|\\d+\\.)\\s", // Headers & Lists
                "STRING" to "`(?:[^`]+)`|\\*\\*(?:[^*]+)\\*\\*|__(?:[^_]+)__", // Code inline, bold
                "COMMENT" to "<!--[\\s\\S]*?-->|^\\[.+\\]:.+", // Comments & Links footnotes
                "BUILTIN" to "\\[.+?\\]\\(.+?\\)|\\!\\[.+?\\]\\(.+?\\)" // links & images
            )
            "markup" -> listOf(
                "COMMENT" to "<!--[\\s\\S]*?-->",
                "STRING" to "\"[^\"]*\"|'[^']*'",
                "KEYWORD" to "<\\/?[a-zA-Z0-9_:-]+|\\/?>|xmlns|style|class|id", // Tags and core xml attrs
                "BUILTIN" to "&[a-zA-Z0-9#]+;" // HTML character codes
            )
            "bash" -> listOf(
                "COMMENT" to "#.*",
                "STRING" to "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",
                "KEYWORD" to "\\b(if|then|elif|else|fi|case|in|esac|while|for|do|done|function|exit|return|local)\\b",
                "BUILTIN" to "\\b(echo|printf|cd|pwd|ls|cat|grep|sed|awk|mkdir|rm|cp|mv|chmod|chown)\\b",
                "NUMBER" to numberP
            )
            "css" -> listOf(
                "COMMENT" to "/\\*[\\s\\S]*?\\*/",
                "STRING" to "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",
                "KEYWORD" to "@[a-zA-Z0-9_-]+|\\b(margin|padding|background|color|font-size|font-family|display|position|flex|width|height|border|outline|box-shadow|text-align|justify-content|align-items)\\b",
                "NUMBER" to "\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|ms|s|deg)?\\b",
                "BUILTIN" to "\\b(important|auto|none|block|inline|flex|grid|absolute|relative|fixed|static|center|left|right|solid|dashed)\\b"
            )
            else -> emptyList()
        }
    }

    private fun getOrCreateRegexList(lang: String): List<Pair<String, Regex>> {
        if (lang == "text") return emptyList()
        return regexListCache.getOrPut(lang) {
            val patterns = getPatternsForLanguage(lang)
            patterns.map { (styleName, regexStr) ->
                styleName to Regex(regexStr)
            }
        }
    }

    fun highlight(
        text: String,
        language: String,
        theme: HighlightTheme
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val patternRegexes = getOrCreateRegexList(language)
        if (patternRegexes.isEmpty()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            append(text)
            val acceptedRanges = mutableListOf<Pair<Int, Int>>() // list of [start, end)
            
            for ((styleName, regex) in patternRegexes) {
                try {
                    val matches = regex.findAll(text)
                    for (match in matches) {
                        val start = match.range.first
                        val end = match.range.last + 1
                        
                        // Check if it overlaps with any already accepted range of higher priority
                        val hasOverlap = acceptedRanges.any { (s, e) ->
                            kotlin.math.max(start, s) < kotlin.math.min(end, e)
                        }
                        if (!hasOverlap) {
                            acceptedRanges.add(Pair(start, end))
                            val style = when (styleName) {
                                "COMMENT" -> SpanStyle(color = theme.comment, fontStyle = FontStyle.Italic)
                                "STRING" -> SpanStyle(color = theme.string)
                                "KEYWORD" -> SpanStyle(color = theme.keyword, fontWeight = FontWeight.Bold)
                                "NUMBER" -> SpanStyle(color = theme.number)
                                "BUILTIN" -> SpanStyle(color = theme.typeOrBuiltIn)
                                else -> SpanStyle(color = theme.textPrimary)
                            }
                            addStyle(style, start, end)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore regex matches on fallback
                }
            }
        }
    }
}
