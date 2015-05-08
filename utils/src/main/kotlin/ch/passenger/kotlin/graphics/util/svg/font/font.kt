package ch.passenger.kotlin.graphics.util.svg.font

import ch.passenger.kotlin.graphics.util.*
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory
import org.slf4j.LoggerFactory
import org.unbescape.xml.XmlEscape
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern
import javax.xml.stream.XMLStreamConstants.*

/**
 * Created by svd on 07/05/2015.
 */
class SVGFont(val family:String, val ems:Float, val xadv:Float, val ascent:Float, val descent:Float, glyphs:Iterable<SVGGlyph>) {

    val names : Map<String, SVGGlyph>
    init {
        var nm = hashMapOf<String,SVGGlyph>()
        glyphs.forEach { nm[it.name] = it }
        names = nm
    }

    companion object {
        fun read(r:Reader, optnames:Map<Char,String> = hashMapOf()) : SVGFont? {
            var font : SVGFont? = null
            class FDesc
            val xin = XMLStreamReaderFactory.create(InputSource(r), true)
            var family : String = ""
            var horizadv : Float = 0f
            xmlprocess(xin) {
                log.debug("adding on handler")
                on("font") {
                    val glyphs = hashSetOf<SVGGlyph>()
                    horizadv = it.attribute("horiz-adv-x","0").toFloat()
                    family = it.attribute("id")
                    var ems = 0f; var ascent=0f; var descent=0f
                    handler {
                        //<font-face units-per-em="1792" ascent="1536" descent="-256" />
                        on("font-face") {
                            ems = it.attribute("units-per-em", "0").toFloat()
                            ascent = it.attribute("ascent", "0").toFloat()
                            descent = it.attribute("descent", "0").toFloat()
                        }
                        on("glyph") {
                            var name = it.attribute("name", "")
                            val unesc = XmlEscape.unescapeXml(it.attribute("unicode", "0"))
                            log.info("xml: ${it.attribute("unicode", "0")} escapes to $unesc")
                            val unicode : Char = unesc.charAt(0)
                            var ghorizadv = it.attribute("horiz-adv-x","0").toFloat()
                            if(!it.attributes.map { it.n }.contains("horiz-adv-x")) ghorizadv=horizadv

                            if(name.isEmpty() && unicode in optnames) {
                                name = optnames[unicode]!!
                            }
                            if(name.isEmpty()) name = "$unicode"
                            glyphs add SVGGlyph(name, unicode, ghorizadv, it.attribute("d", ""))
                        }
                    }
                    log.info("glyphs: ${glyphs.size()}")
                    font = SVGFont(family, ems, horizadv, ascent, descent, glyphs)
                }
            }
            return font
        }
    }
}

class SVGGlyph(val name:String, val unicode:Char, val xadv:Float, val path : String)

class FontawesomeNameMapper(r:Reader) {
    val names :Map<Char,String>
    init {
        var map : Map<Char,String> = hashMapOf()
        val p = Pattern.compile("[\$]fa-var-([a-zA-Z-]+).*([a-fA-F0-9]{4,16}).*")
        r.forEachLine {
            val m = p.matcher(it)
            if(m.matches()) {
                val ci : Int = (Integer.parseInt(m[2], 16)  )
                map += ci.toChar() to m[1]
            }
        }
        //map.forEach { println("${it.key} -> ${"%x".format(it.value.toInt())}") }
        names = map
    }

}

fun main(args: Array<String>) {
    ch.passenger.kotlin.graphics.util.logging.Configure()
    val res = FontawesomeNameMapper::class.javaClass.getResourceAsStream("/fontawesome/_variables.scss")
    val map = FontawesomeNameMapper(BufferedReader(InputStreamReader(res)))
    val resf = SVGFont::class.javaClass.getResourceAsStream("/fontawesome/fontawesome-webfont.svg")
    val font = SVGFont.read(BufferedReader(InputStreamReader(resf)), map.names)
    if(font!=null) {
        println("${font.family} ${font.ems} ${font.ascent} ${font.descent}")
        font.names.forEach {
            println("${it.key} -> ${it.value.unicode} ${it.value.path}")
        }
    }


}