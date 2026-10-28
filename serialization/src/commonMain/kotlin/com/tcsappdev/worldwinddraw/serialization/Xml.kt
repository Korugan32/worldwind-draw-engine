package com.tcsappdev.worldwinddraw.serialization

// A small XML reader for what KML needs, and nothing more: elements, attributes, text, CDATA, the five named entities and numeric ones.
// There is no XML library for every platform this library builds for, and KML is plain XML. What it deliberately does NOT do: it never
// expands a DOCTYPE's entities (so there is no way to make it read files or blow up a text with nested entities), it does not validate,
// and namespaces are only stripped (`gx:Track` is read as `Track`): in KML the names of the elements are what matters.

internal class XmlNode(val name: String, val attributes: Map<String, String>) {
    val children = ArrayList<XmlNode>()
    val text = StringBuilder()

    /** The text directly inside this element, trimmed. */
    fun text(): String = text.toString().trim()

    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    fun children(name: String): List<XmlNode> = children.filter { it.name == name }

    /** The text of the child [name], or `null` if there is none or it is empty. */
    fun childText(name: String): String? = child(name)?.text()?.takeIf { it.isNotEmpty() }
}

internal object Xml {
    private const val MAX_DEPTH = 256

    /** The root element of [text]. @throws DrawFormatException if it is not well-formed (a tag that is never closed, a mismatched one, nothing at all). */
    fun parse(text: String): XmlNode {
        return Reader(text.removePrefix("\uFEFF")).document()
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun document(): XmlNode {
            var root: XmlNode? = null
            val open = ArrayList<XmlNode>()
            while (i < s.length) {
                if (s[i] != '<') {
                    val end = s.indexOf('<', i).let { if (it < 0) s.length else it }
                    val raw = s.substring(i, end)
                    if (open.isNotEmpty()) open.last().text.append(unescape(raw))
                    else if (raw.isNotBlank()) fail("text outside the root element")
                    i = end
                } else if (s.startsWith("<!--", i)) {
                    i = skipPast("-->", "a comment that never ends")
                } else if (s.startsWith("<![CDATA[", i)) {
                    val end = s.indexOf("]]>", i)
                    if (end < 0) fail("a CDATA section that never ends")
                    if (open.isEmpty()) fail("CDATA outside the root element")
                    open.last().text.append(s, i + 9, end)
                    i = end + 3
                } else if (s.startsWith("<?", i)) {
                    i = skipPast("?>", "an instruction that never ends")
                } else if (s.startsWith("<!", i)) {
                    i = skipDeclaration()
                } else if (s.startsWith("</", i)) {
                    val end = s.indexOf('>', i)
                    if (end < 0) fail("a closing tag that never ends")
                    val name = local(s.substring(i + 2, end).trim())
                    val node = open.removeLastOrNull() ?: fail("</$name> closes nothing")
                    if (node.name != name) fail("</$name> where </${node.name}> was expected")
                    i = end + 1
                    if (open.isEmpty()) root = root ?: node
                } else {
                    if (open.isEmpty() && root != null) fail("more than one root element")
                    val (node, selfClosing) = startTag()
                    open.lastOrNull()?.children?.add(node)
                    if (root == null && open.isEmpty()) root = node
                    if (!selfClosing) {
                        if (open.size >= MAX_DEPTH) fail("elements nested deeper than $MAX_DEPTH")
                        open.add(node)
                    }
                }
            }
            if (open.isNotEmpty()) fail("<${open.last().name}> is never closed")
            return root ?: fail("no element")
        }

        private fun startTag(): Pair<XmlNode, Boolean> {
            i++ // <
            val nameStart = i
            while (i < s.length && !s[i].isWhitespace() && s[i] != '>' && s[i] != '/') i++
            val name = local(s.substring(nameStart, i))
            if (name.isEmpty()) fail("a tag without a name")
            val attributes = LinkedHashMap<String, String>()
            while (true) {
                while (i < s.length && s[i].isWhitespace()) i++
                if (i >= s.length) fail("a tag that never ends")
                if (s[i] == '>') { i++; return XmlNode(name, attributes) to false }
                if (s[i] == '/') {
                    if (i + 1 < s.length && s[i + 1] == '>') { i += 2; return XmlNode(name, attributes) to true }
                    fail("a stray / in <$name>")
                }
                val keyStart = i
                while (i < s.length && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/') i++
                val key = local(s.substring(keyStart, i))
                while (i < s.length && s[i].isWhitespace()) i++
                if (i >= s.length || s[i] != '=') fail("attribute \"$key\" of <$name> has no value")
                i++
                while (i < s.length && s[i].isWhitespace()) i++
                val quote = s.getOrNull(i)
                if (quote != '"' && quote != '\'') fail("attribute \"$key\" of <$name> is not quoted")
                val end = s.indexOf(quote, i + 1)
                if (end < 0) fail("attribute \"$key\" of <$name> never ends")
                attributes[key] = unescape(s.substring(i + 1, end))
                i = end + 1
            }
        }

        // <!DOCTYPE ...> may hold a [ ... ] subset with > inside it; it is skipped whole and never interpreted.
        private fun skipDeclaration(): Int {
            var depth = 0
            var j = i
            while (j < s.length) {
                when (s[j]) {
                    '[' -> depth++
                    ']' -> depth--
                    '>' -> if (depth <= 0) return j + 1
                }
                j++
            }
            fail("a declaration that never ends")
        }

        private fun skipPast(end: String, what: String): Int {
            val at = s.indexOf(end, i)
            if (at < 0) fail(what)
            return at + end.length
        }

        private fun fail(what: String): Nothing = throw DrawFormatException("not well-formed XML: $what (at character $i)")
    }

    private fun local(name: String) = name.substringAfter(':')

    private val named = mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'")

    /** `&lt;` and its kind, and `&#65;` / `&#x41;`; anything else that looks like an entity is kept as written. */
    fun unescape(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val end = if (c == '&') text.indexOf(';', i) else -1
            val replacement = if (end in (i + 2)..(i + 10)) entity(text.substring(i + 1, end)) else null
            if (replacement != null) { out.append(replacement); i = end + 1 } else { out.append(c); i++ }
        }
        return out.toString()
    }

    private fun entity(body: String): String? {
        named[body]?.let { return it }
        if (!body.startsWith("#")) return null
        val code = if (body.startsWith("#x") || body.startsWith("#X")) body.drop(2).toIntOrNull(16) else body.drop(1).toIntOrNull()
        if (code == null || code !in 0..0x10FFFF || code in 0xD800..0xDFFF) return null
        return if (code < 0x10000) code.toChar().toString() else {
            val c = code - 0x10000
            charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
        }
    }

    /** [text] made safe to put between tags or in a quoted attribute. */
    fun escape(text: String): String {
        if (text.none { it == '&' || it == '<' || it == '>' || it == '"' || it == '\'' }) return text
        val out = StringBuilder()
        for (c in text) when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> out.append(c)
        }
        return out.toString()
    }
}
