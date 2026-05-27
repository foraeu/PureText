package com.example.utils

data class OutlineSymbol(
    val label: String,
    val lineIndex: Int,
    val type: SymbolType
)

enum class SymbolType {
    CLASS, FUNCTION, HEADER
}

object OutlineParser {

    fun parseSymbols(lines: List<String>, language: String): List<OutlineSymbol> {
        if (lines.isEmpty()) return emptyList()
        val symbols = mutableListOf<OutlineSymbol>()

        when (language.lowercase()) {
            "kotlin", "java" -> {
                val classRegex = Regex("^[\\s]*?(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?(?:open\\s+|sealed\\s+|data\\s+|inner\\s+|companion\\s+)?(?:class|interface|object)\\s+([a-zA-Z_]\\w*)")
                val funRegex = Regex("^[\\s]*?(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?(?:open\\s+|override\\s+|suspend\\s+|inline\\s+)?fun\\s+([a-zA-Z_]\\w*)")
                val javaMethodRegex = Regex("^[\\s]*?(?:public|private|protected|static|final|synchronized|\\s)+[a-zA-Z0-9_<>\\[\\]]+(?:\\s+|\\[\\])+([a-zA-Z_]\\w*)\\s*\\(")

                lines.forEachIndexed { idx, line ->
                    val classMatch = classRegex.find(line)
                    if (classMatch != null) {
                        symbols.add(OutlineSymbol(classMatch.groupValues[1], idx, SymbolType.CLASS))
                        return@forEachIndexed
                    }
                    val funMatch = funRegex.find(line)
                    if (funMatch != null) {
                        symbols.add(OutlineSymbol(funMatch.groupValues[1] + "()", idx, SymbolType.FUNCTION))
                        return@forEachIndexed
                    }
                    val javaMethodMatch = javaMethodRegex.find(line)
                    if (javaMethodMatch != null) {
                        val name = javaMethodMatch.groupValues[1]
                        if (name != "if" && name != "for" && name != "while" && name != "switch" && name != "catch" && name != "synchronized") {
                            symbols.add(OutlineSymbol("$name()", idx, SymbolType.FUNCTION))
                        }
                    }
                }
            }
            "python" -> {
                val classRegex = Regex("^[\\s]*?class\\s+([a-zA-Z_]\\w*)")
                val defRegex = Regex("^[\\s]*?def\\s+([a-zA-Z_]\\w*)")
                lines.forEachIndexed { idx, line ->
                    val classMatch = classRegex.find(line)
                    if (classMatch != null) {
                        symbols.add(OutlineSymbol(classMatch.groupValues[1], idx, SymbolType.CLASS))
                        return@forEachIndexed
                    }
                    val defMatch = defRegex.find(line)
                    if (defMatch != null) {
                        symbols.add(OutlineSymbol(defMatch.groupValues[1] + "()", idx, SymbolType.FUNCTION))
                    }
                }
            }
            "rust" -> {
                val structRegex = Regex("^[\\s]*?(?:pub\\s+)?(?:struct|enum|trait|mod)\\s+([a-zA-Z_]\\w*)")
                val fnRegex = Regex("^[\\s]*?(?:pub\\s+)?(?:async\\s+)?fn\\s+([a-zA-Z_]\\w*)")
                lines.forEachIndexed { idx, line ->
                    val structMatch = structRegex.find(line)
                    if (structMatch != null) {
                        symbols.add(OutlineSymbol(structMatch.groupValues[1], idx, SymbolType.CLASS))
                        return@forEachIndexed
                    }
                    val fnMatch = fnRegex.find(line)
                    if (fnMatch != null) {
                        symbols.add(OutlineSymbol(fnMatch.groupValues[1] + "()", idx, SymbolType.FUNCTION))
                    }
                }
            }
            "typescript", "javascript" -> {
                val classRegex = Regex("^[\\s]*?(?:export\\s+)?class\\s+([a-zA-Z_]\\w*)")
                val funRegex = Regex("^[\\s]*?(?:export\\s+)?(?:async\\s+)?function\\s+([a-zA-Z_]\\w*)")
                lines.forEachIndexed { idx, line ->
                    val classMatch = classRegex.find(line)
                    if (classMatch != null) {
                        symbols.add(OutlineSymbol(classMatch.groupValues[1], idx, SymbolType.CLASS))
                        return@forEachIndexed
                    }
                    val funMatch = funRegex.find(line)
                    if (funMatch != null) {
                        symbols.add(OutlineSymbol(funMatch.groupValues[1] + "()", idx, SymbolType.FUNCTION))
                    }
                }
            }
            "r" -> {
                val funRegex = Regex("^[\\s]*?([a-zA-Z_][\\w.]*)[\\s]*(?:<-|=)[\\s]*function[\\s]*\\(")
                lines.forEachIndexed { idx, line ->
                    val funMatch = funRegex.find(line)
                    if (funMatch != null) {
                        symbols.add(OutlineSymbol(funMatch.groupValues[1] + "()", idx, SymbolType.FUNCTION))
                    }
                }
            }
            "epub" -> {
                val chapterRegex = Regex("^(第[0-9一二三四五六七八九十百千万]+[章节回卷]|卷[0-9一二三四五六七八九十百千万]+)\\s*(.*)$")
                lines.forEachIndexed { idx, line ->
                    val match = chapterRegex.find(line)
                    if (match != null) {
                        symbols.add(OutlineSymbol(line, idx, SymbolType.HEADER))
                    }
                }
            }
            "markdown" -> {
                val headerRegex = Regex("^(#{1,6})\\s+(.*)$")
                lines.forEachIndexed { idx, line ->
                    val match = headerRegex.find(line)
                    if (match != null) {
                        val level = match.groupValues[1].length
                        val title = match.groupValues[2]
                        val indent = "  ".repeat(level - 1)
                        symbols.add(OutlineSymbol(indent + title, idx, SymbolType.HEADER))
                    }
                }
            }
        }
        return symbols
    }
}
