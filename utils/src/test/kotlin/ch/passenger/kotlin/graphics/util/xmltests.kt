package ch.passenger.kotlin.graphics.util

import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import org.testng.AssertJUnit.*
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.stream.*

/**
 * Created by svd on 05/05/2015.
 */
class TestXML {
    val log = LoggerFactory.getLogger(TestXML::class.java)
    data class Content(val id:Long, val storage:List<Int>, val fraction:Float) : XMLWritable<Content> {
        override fun writeXML(wr: XMLStreamWriter) {
            wr.writeStartElement(element)
            wr.writeAttribute("id", "$id")
            wr.writeAttribute("storage", storage.joinToString(","))
            wr.writeAttribute("fraction", "$fraction")
            wr.writeEndElement()
        }

        companion object {
            val element: String = "content"
            init {
                XMLWriteableRegistry.register(Content::class, object : XMLWritableFactory<Any?> {
                    override val name: String = element
                    override fun readXML(r: XMLStreamReader): Any? {
                        val id = r.attribute("id").toLong()
                        val storage = r.attribute("storage").split(",").map { it.toInt() }
                        val fraction = r.attribute("fraction").toFloat()
                        return Content(id, storage, fraction)
                    }
                })
            }
        }
    }
    data class Stuff(val n:String, val content:List<Content>) : XMLWritable<Stuff> {
        override fun writeXML(wr: XMLStreamWriter) {
            wr.writeStartElement(element)
            wr.writeAttribute("name", n)
            XMLWriteableRegistry.write(wr, content, "contents")

            wr.writeEndElement()
        }

        companion object {
            val element : String = "stuff"

            init {
                XMLWriteableRegistry.register(Stuff::class, object : XMLWritableFactory<Stuff> {
                    override val name: String = element
                    override fun readXML(r: XMLStreamReader): Stuff {
                        val n = r.attribute("name")
                        var cl: Iterable<Content>? = null
                        while (r.next() != XMLStreamConstants.END_ELEMENT) {
                            when (r.eventType) {
                                XMLStreamConstants.START_ELEMENT -> {
                                    if (r.localName == "contents") {
                                        cl = XMLWriteableRegistry.readCollection(r, Content::class)
                                    } else {
                                        XMLWriteableRegistry.gobble(r.localName, r)
                                    }
                                }
                            }
                        }
                        return Stuff(n, cl?.toList() ?: emptyList())
                    }
                })
                XMLWriteableRegistry.registerToplevel(Stuff::class, "suitcase")
            }
        }
    }

    @Test
    fun writeTest() {
        val c = Content(11L, listOf(1, 2, 3), 3.14f)
        val baos = ByteArrayOutputStream()
        val wr = XMLStreamWriterFactory.create(baos)
        wr.writeStartDocument()
        c.writeXML(wr)
        wr.writeEndDocument()
        wr.flush();wr.close()
        val s = String(baos.toByteArray())
        log.info("xml: $s")
    }

    @Test
    fun readTest() {
        val c = Content(11L, listOf(1, 2, 3), 3.14f)
        val baos = ByteArrayOutputStream()
        val wr = XMLStreamWriterFactory.create(baos)
        wr.writeStartDocument()
        c.writeXML(wr)
        wr.writeEndDocument()
        wr.flush();wr.close()
        val s = String(baos.toByteArray())
        log.info("xml: $s")
        val r = XMLStreamReaderFactory.create(InputSource(ByteArrayInputStream(baos.toByteArray())), true)
        val read = XMLWriteableRegistry.read(r)
        assert(read.count()>0)
        read.forEach { assert(it is Content); log.info("el: $it")}
    }

    @Test
    fun deeptWrite() {
        val stuff = listOf(Stuff("one", listOf(Content(1L, listOf(2, 4, 6), 1f), Content(2L, listOf(1, 3, 5), 3.14f))),
                Stuff("two", listOf(Content(3L, listOf(1, 2, 3, 5, 7, 11), 2.2f), Content(4L, listOf(13, 17, 19), 2.2f))))
        val baos = ByteArrayOutputStream()
        val wr = XMLOutputFactory.newInstance().createXMLStreamWriter(baos)
        wr.writeStartDocument()
        wr.writeStartElement("suitcase")
        stuff.forEach { it.writeXML(wr) }
        wr.writeEndElement()
        wr.writeEndDocument()
        wr.flush(); wr.close()
        log.info("xml: ${String(baos.toByteArray())}")
        val r = XMLInputFactory.newInstance().createXMLStreamReader(ByteArrayInputStream(baos.toByteArray()))
        val res = XMLWriteableRegistry.read(r)

        assertEquals(stuff.size(), res.count())
        res.forEach { log.info("$it") }
    }

}