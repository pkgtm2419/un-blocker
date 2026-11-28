package com.unblocker.app.data.export

/**
 * Pure-Kotlin UTF-8 JSON serializer and bounded recursive-descent parser.
 *
 * Avoids `android.jar`'s `org.json` stub so export and backup codecs execute identically in pure-JVM
 * unit tests and Android runtime without external JSON dependencies.
 */
internal object PureJsonCodec {
    private const val MAX_JSON_DEPTH = 16

    fun stringifyObject(
        map: Map<String, Any?>,
        indentSpaces: Int = 2,
    ): String {
        val sb = StringBuilder()
        writeMap(sb, map, indentSpaces, depth = 0)
        return sb.toString()
    }

    fun parseObject(raw: String): Map<String, Any?> {
        val parser = Parser(raw, MAX_JSON_DEPTH)
        val parsed = parser.parseRootObject()
        parser.expectEnd()
        return parsed
    }

    fun optObject(
        map: Map<String, Any?>,
        key: String,
    ): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? Map<String, Any?>
    }

    fun optArray(
        map: Map<String, Any?>,
        key: String,
    ): List<Any?>? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? List<Any?>
    }

    fun optString(
        map: Map<String, Any?>,
        key: String,
        defaultValue: String = "",
    ): String = map[key] as? String ?: defaultValue

    fun optInt(
        map: Map<String, Any?>,
        key: String,
        defaultValue: Int,
    ): Int {
        val number = map[key] as? Number ?: return defaultValue
        return number.toInt()
    }

    fun optBoolean(
        map: Map<String, Any?>,
        key: String,
        defaultValue: Boolean,
    ): Boolean = map[key] as? Boolean ?: defaultValue

    private fun writeValue(
        out: StringBuilder,
        value: Any?,
        indentSpaces: Int,
        depth: Int,
    ) {
        when (value) {
            null -> {
                out.append("null")
            }

            is Boolean -> {
                out.append(value.toString())
            }

            is Number -> {
                out.append(value.toString())
            }

            is String -> {
                appendEscapedString(out, value)
            }

            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeMap(out, value as Map<String, Any?>, indentSpaces, depth)
            }

            is Iterable<*> -> {
                writeList(out, value, indentSpaces, depth)
            }

            else -> {
                appendEscapedString(out, value.toString())
            }
        }
    }

    private fun writeMap(
        out: StringBuilder,
        map: Map<String, Any?>,
        indentSpaces: Int,
        depth: Int,
    ) {
        if (map.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{\n")
        val childIndent = " ".repeat((depth + 1) * indentSpaces)
        val entries = map.entries.toList()
        entries.forEachIndexed { idx, (key, valItem) ->
            out.append(childIndent)
            appendEscapedString(out, key)
            out.append(": ")
            writeValue(out, valItem, indentSpaces, depth + 1)
            if (idx < entries.lastIndex) out.append(',')
            out.append('\n')
        }
        out.append(" ".repeat(depth * indentSpaces)).append('}')
    }

    private fun writeList(
        out: StringBuilder,
        items: Iterable<*>,
        indentSpaces: Int,
        depth: Int,
    ) {
        val list = items.toList()
        if (list.isEmpty()) {
            out.append("[]")
            return
        }
        out.append("[\n")
        val childIndent = " ".repeat((depth + 1) * indentSpaces)
        list.forEachIndexed { idx, item ->
            out.append(childIndent)
            writeValue(out, item, indentSpaces, depth + 1)
            if (idx < list.lastIndex) out.append(',')
            out.append('\n')
        }
        out.append(" ".repeat(depth * indentSpaces)).append(']')
    }

    private fun appendEscapedString(
        out: StringBuilder,
        value: String,
    ) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> {
                    out.append("\\\"")
                }

                '\\' -> {
                    out.append("\\\\")
                }

                '\b' -> {
                    out.append("\\b")
                }

                '\u000C' -> {
                    out.append("\\f")
                }

                '\n' -> {
                    out.append("\\n")
                }

                '\r' -> {
                    out.append("\\r")
                }

                '\t' -> {
                    out.append("\\t")
                }

                else -> {
                    if (ch < ' ') {
                        out.append("\\u")
                        out.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        out.append('"')
    }

    private class Parser(
        private val src: String,
        private val maxDepth: Int,
    ) {
        private var pos = 0

        fun parseRootObject(): Map<String, Any?> {
            skipWhitespace()
            if (peek() != '{') throw IllegalArgumentException("Expected JSON object")
            return parseObject(depth = 0)
        }

        fun expectEnd() {
            skipWhitespace()
            if (pos != src.length) {
                throw IllegalArgumentException("Unexpected trailing JSON content")
            }
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > maxDepth) {
                throw IllegalArgumentException("JSON nesting exceeds max depth ($maxDepth)")
            }
            skipWhitespace()
            return when (val ch = peek()) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected character '$ch' at $pos")
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            if (depth >= maxDepth) {
                throw IllegalArgumentException("JSON nesting exceeds max depth ($maxDepth)")
            }
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw IllegalArgumentException("Expected object key at $pos")
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue(depth + 1)
                result[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        pos++
                    }

                    '}' -> {
                        pos++
                        return result
                    }

                    else -> {
                        throw IllegalArgumentException("Expected ',' or '}' at $pos")
                    }
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            if (depth >= maxDepth) {
                throw IllegalArgumentException("JSON nesting exceeds max depth ($maxDepth)")
            }
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result.add(parseValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        pos++
                    }

                    ']' -> {
                        pos++
                        return result
                    }

                    else -> {
                        throw IllegalArgumentException("Expected ',' or ']' at $pos")
                    }
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when {
                    ch == '"' -> return sb.toString()
                    ch < ' ' -> throw IllegalArgumentException("Unescaped control character in string")
                    ch == '\\' -> sb.append(parseEscape())
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun parseEscape(): Char {
            if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
            return when (val esc = src[pos++]) {
                '"' -> {
                    '"'
                }

                '\\' -> {
                    '\\'
                }

                '/' -> {
                    '/'
                }

                'b' -> {
                    '\b'
                }

                'f' -> {
                    '\u000C'
                }

                'n' -> {
                    '\n'
                }

                'r' -> {
                    '\r'
                }

                't' -> {
                    '\t'
                }

                'u' -> {
                    if (pos + 4 > src.length) throw IllegalArgumentException("Short unicode escape")
                    val hex = src.substring(pos, pos + 4)
                    pos += 4
                    hex.toInt(16).toChar()
                }

                else -> {
                    throw IllegalArgumentException("Invalid escape '\\$esc'")
                }
            }
        }

        private fun parseBoolean(): Boolean =
            if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Invalid boolean literal at $pos")
            }

        private fun parseNull(): Any? {
            if (!src.startsWith("null", pos)) {
                throw IllegalArgumentException("Invalid null literal at $pos")
            }
            pos += 4
            return null
        }

        private fun parseNumber(): Number {
            val start = pos
            if (peek() == '-') pos++
            readDigits(required = true)
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                readDigits(required = true)
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                readDigits(required = true)
            }
            val token = src.substring(start, pos)
            return if (isFloat) {
                token.toDouble()
            } else {
                token.toLongOrNull() ?: token.toDouble()
            }
        }

        private fun readDigits(required: Boolean) {
            val start = pos
            while (pos < src.length && src[pos] in '0'..'9') {
                pos++
            }
            if (required && pos == start) {
                throw IllegalArgumentException("Expected digit at $pos")
            }
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) {
                pos++
            }
        }

        private fun expect(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos++
        }

        private fun peek(): Char =
            if (pos < src.length) src[pos] else '\u0000'
    }
}
