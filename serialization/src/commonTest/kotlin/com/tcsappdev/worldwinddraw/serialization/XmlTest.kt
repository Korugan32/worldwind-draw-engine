package com.tcsappdev.worldwinddraw.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XmlTest {
    private fun fails(text: String) = assertFailsWith<DrawFormatException> { Xml.parse(text) }.message.orEmpty()

    @Test
    fun elementsAttributesAndTextAreRead() {
        val root = Xml.parse("""<a x="1" y='two'><b>hello</b><c/><b>again</b></a>""")
        assertEquals("a", root.name)
        assertEquals(mapOf("x" to "1", "y" to "two"), root.attributes)
        assertEquals(listOf("b", "c", "b"), root.children.map { it.name })
        assertEquals(listOf("hello", "again"), root.children("b").map { it.text() })
        assertEquals("hello", root.childText("b"))
        assertEquals(null, root.childText("c"))
        assertEquals(null, root.childText("missing"))
    }

    @Test
    fun textKeepsItsInnerSpacesAndIsTrimmedAtTheEnds() {
        assertEquals("a  b", Xml.parse("<a>\n  a  b \n</a>").text())
    }

    @Test
    fun theNamedAndNumericEntitiesAreDecoded() {
        assertEquals("<&>\"' A A 😀", Xml.parse("<a>&lt;&amp;&gt;&quot;&apos; &#65; &#x41; &#x1F600;</a>").text())
        assertEquals("a&b", Xml.parse("""<a v="a&amp;b"/>""").attributes["v"])
    }

    @Test
    fun anEntityThatIsNotKnownIsKeptAsWritten() {
        assertEquals("&nbsp; &#xZZ; &#1114112; & done", Xml.parse("<a>&nbsp; &#xZZ; &#1114112; & done</a>").text())
    }

    @Test
    fun cdataIsTakenLiterally() {
        assertEquals("<b>&amp; not decoded</b>", Xml.parse("<a><![CDATA[<b>&amp; not decoded</b>]]></a>").text())
    }

    @Test
    fun commentsInstructionsAndTheXmlDeclarationAreSkipped() {
        val root = Xml.parse("""<?xml version="1.0"?><!-- a > comment --><a><!-- <b/> --><?pi x?><c/></a>""")
        assertEquals(listOf("c"), root.children.map { it.name })
    }

    @Test
    fun aDoctypeIsSkippedAndItsEntitiesAreNeverExpanded() {
        val text = """<!DOCTYPE a [ <!ENTITY xxe "SECRET"> <!ENTITY b "<c/>"> ]><a>&xxe;</a>"""
        assertEquals("&xxe;", Xml.parse(text).text())
    }

    @Test
    fun namespacePrefixesAreStripped() {
        val root = Xml.parse("""<kml:kml xmlns:kml="http://x"><gx:Track kml:id="t"/></kml:kml>""")
        assertEquals("kml", root.name)
        assertEquals("Track", root.children.single().name)
        assertEquals("t", root.children.single().attributes["id"])
    }

    @Test
    fun aByteOrderMarkAtTheStartIsIgnored() {
        assertEquals("a", Xml.parse("\uFEFF<a/>").name)
    }

    @Test
    fun aTagThatIsNeverClosedIsRefused() {
        assertTrue("never closed" in fails("<a><b>"))
    }

    @Test
    fun aMismatchedClosingTagIsRefused() {
        assertTrue("</b>" in fails("<a></b></a>"))
        assertTrue("</a>" in fails("<a><b></a></b>"))
    }

    @Test
    fun aClosingTagThatClosesNothingIsRefused() {
        assertTrue("closes nothing" in fails("</a>"))
    }

    @Test
    fun noElementAtAllIsRefused() {
        fails("")
        fails("   ")
        fails("<!-- only a comment -->")
    }

    @Test
    fun twoRootElementsAreRefused() {
        assertTrue("more than one root" in fails("<a/><b/>"))
    }

    @Test
    fun textOutsideTheRootIsRefused() {
        fails("hello<a/>")
        fails("<a/>trailing")
    }

    @Test
    fun anAttributeWithoutQuotesOrValueIsRefused() {
        fails("<a x=1/>")
        fails("<a x/>")
        fails("""<a x="1/>""")
    }

    @Test
    fun brokenMarkupIsRefusedNotLoopedOn() {
        for (bad in listOf("<", "<a", "<a ", "<a x=", "</", "<!--", "<![CDATA[", "<?", "<!DOCTYPE", "<a/", "<a / >")) fails(bad)
    }

    @Test
    fun deepNestingIsRefusedBeforeItCanOverflowAnything() {
        assertTrue("deeper" in fails("<a>".repeat(300) + "</a>".repeat(300)))
        Xml.parse("<a>".repeat(200) + "</a>".repeat(200))
    }

    @Test
    fun escapeAndUnescapeAreInverse() {
        val text = "a < b & c > d \"e\" 'f'"
        assertEquals(text, Xml.unescape(Xml.escape(text)))
        assertEquals("a &lt; b &amp; c", Xml.escape("a < b & c"))
        assertEquals("plain", Xml.escape("plain"))
    }
}
