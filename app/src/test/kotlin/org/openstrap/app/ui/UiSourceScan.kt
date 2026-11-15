package org.openstrap.app.ui

import java.io.File

/**
 * The scanning helpers the design-rule tests share (DesignTokenTest, HaloRulesTest): the Kotlin under ui/ as code only,
 * and the bracket matching that reads a call's arguments or a function's body.
 */
internal class UiSources {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    val uiRoot = File(root, "kotlin/org/openstrap/app/ui")

    /** Every Kotlin file under ui/, by its path from ui/, as code only (comments and string contents blanked). */
    val sources: Map<String, List<String>> by lazy {
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }
            .associate { it.relativeTo(uiRoot).invariantSeparatorsPath to codeOnly(it.readText()).lines() }
    }

    /** Each line that matches [pattern], as "path:line  text", skipping the files in [except]. */
    fun find(pattern: Regex, except: Set<String> = emptySet()): List<String> = sources.flatMap { (path, lines) ->
        if (path in except) emptyList()
        else lines.mapIndexedNotNull { i, line -> if (pattern.containsMatchIn(line)) "$path:${i + 1}  ${line.trim()}" else null }
    }

    /** The code of [path] as one string. */
    fun code(path: String): String = sources.getValue(path).joinToString("\n")

    /** Each call of a function matching [name] in the files [only] (all when null), as (path, line, arguments). */
    fun calls(name: Regex, only: Collection<String>? = null): List<Triple<String, Int, String>> = sources.flatMap { (path, lines) ->
        if (only != null && path !in only) return@flatMap emptyList()
        val code = lines.joinToString("\n")
        Regex("""\b(?:${name.pattern})\(""").findAll(code).map { m ->
            Triple(path, code.substring(0, m.range.first).count { it == '\n' } + 1, balanced(code, m.range.last))
        }.toList()
    }
}

/** The text between the `{` at [open] and its matching `}`. */
internal fun braced(code: String, open: Int): String {
    var depth = 0
    for (i in open until code.length) {
        when (code[i]) {
            '{' -> depth++
            '}' -> if (--depth == 0) return code.substring(open + 1, i)
        }
    }
    return code.substring(open + 1)
}

/** The text between the `(` at [open] and its matching `)`. */
internal fun balanced(code: String, open: Int): String {
    var depth = 0
    for (i in open until code.length) {
        when (code[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) return code.substring(open + 1, i)
        }
    }
    return code.substring(open + 1)
}

/**
 * [src] with comments and the contents of string and char literals blanked, line breaks kept, so a scan sees only
 * code and line numbers still match. Template code inside strings is blanked too.
 */
internal fun codeOnly(src: String): String {
    val out = StringBuilder(src.length)
    var i = 0
    fun blank(c: Char) = out.append(if (c == '\n') '\n' else ' ')
    while (i < src.length) {
        val c = src[i]
        when {
            src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') { blank(src[i]); i++ }
            src.startsWith("/*", i) -> {
                var depth = 0
                do {
                    when {
                        src.startsWith("/*", i) -> { depth++; blank(src[i]); blank(src[i + 1]); i += 2 }
                        src.startsWith("*/", i) -> { depth--; blank(src[i]); blank(src[i + 1]); i += 2 }
                        else -> { blank(src[i]); i++ }
                    }
                } while (depth > 0 && i < src.length)
            }
            src.startsWith("\"\"\"", i) -> {
                out.append("\"\"\""); i += 3
                while (i < src.length && !src.startsWith("\"\"\"", i)) { blank(src[i]); i++ }
                out.append("\"\"\""); i += 3
            }
            c == '"' || c == '\'' -> {
                out.append(c); i++
                while (i < src.length && src[i] != c && src[i] != '\n') {
                    if (src[i] == '\\') { blank(src[i]); i++ }
                    if (i < src.length) { blank(src[i]); i++ }
                }
                if (i < src.length) { out.append(src[i]); i++ }
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}
