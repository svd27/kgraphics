package ch.passenger.kotlin.graphics.mesh.svg

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Mesh
import ch.passenger.kotlin.graphics.mesh.Vertex
import org.parboiled.BaseParser
import org.parboiled.Parboiled
import org.parboiled.Rule
import org.parboiled.parserunners.BasicParseRunner
import org.parboiled.parserunners.ReportingParseRunner
import org.parboiled.parserunners.TracingParseRunner
import org.parboiled.support.ParseTreeUtils
import org.parboiled.support.Var
import org.parboiled.support.DebuggingValueStack
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.jvm.java

/**
 * Created by svd on 09/05/2015.
 */
trait SVGMeshData {
    companion object {
        open data class MD(override val paths: Iterable<SVGPath>, override val path: Int, override val idxPathelement: Int) :SVGPathMeshData
        open data class MDCurve(paths: Iterable<SVGPath>, path: Int, idxPathelement: Int) : MD(paths, path, idxPathelement) {
            private fun cps() : Iterable<VectorF> {
                val pe = paths.elementAt(path).elements[idxPathelement]
                return when(pe) {
                    is SVGCubic -> {
                        pe.coords.mapIndexed { i, v -> i to v }.filter { it.first%3!=0 }.map { it.second }
                    }
                    is SVGSmoothCubic -> {
                        pe.coords.mapIndexed { i, v -> i to v }.filter { it.first%2==0 }.map { it.second }
                    }
                    is SVGQuad -> {
                        pe.coords.mapIndexed { i, v -> i to v }.filter { it.first%2==0 }.map { it.second }
                    }
                    is SVGSmoothQuad -> emptyList<VectorF>()
                    else -> throw IllegalArgumentException()
                }
            }
        }
        fun invoke(paths: Iterable<SVGPath>, path: Int, idxPathelement: Int) : SVGMeshData {
            val pe = paths.elementAt(path).elements[idxPathelement]
            return when(pe) {
                is SVGQuad -> MDCurve(paths, path, idxPathelement)
                else -> MD(paths, path, idxPathelement)
            }
        }
        fun reverse() : SVGMeshData = object : SVGReversePath {}
    }

}
trait SVGReversePath : SVGMeshData
trait SVGPathMeshData : SVGMeshData {
    val paths:Iterable<SVGPath>
    val path : Int
    val idxPathelement : Int
    val pathElement:SVGPathElement get() = paths.elementAt(path).elements[idxPathelement]

}


fun<H:SVGMeshData,V:SVGMeshData,F:SVGMeshData>
        createMesh(paths:Iterable<SVGPath>, extent:AlignedCube?=null,
                   cv:(SVGMeshData,VectorF)->V,
                   ce:(SVGMeshData, Vertex<H, V, F>,Vertex<H, V, F>)->H,
                   cf:(SVGMeshData, parent: Face<H,V,F>, edge:HalfEdge<H,V,F>)->F
                   )  : Mesh<H,V,F> {


    val ex : AlignedCube
    if(extent==null) {
        val min = MutableVectorF(3) {Float.MAX_VALUE}
        val max = MutableVectorF(3) {Float.MIN_VALUE}
        paths.forEach {
            it.elements.forEach {
                it.coords.forEach {
                    it().forEachIndexed { i, fl ->
                        if(min[i]>fl) min[i] = fl
                        if(max[i]<fl) max[i] = fl
                    }
                }
            }
        }
        min().forEachIndexed { i, fl -> if(fl==Float.MAX_VALUE) min[i]=-1f }
        max().forEachIndexed { i, fl -> if(fl==Float.MIN_VALUE) max[i]=1f }
        ex = AlignedCube(min, max)
    } else ex = extent

    val m = Mesh<H,V,F>(ex, { e, parent ->
        cf(e.data, parent, e)
    })

    paths.forEachIndexed { idxPath, path ->
        var current : Vertex<H,V,F>? = null
        path.elements.forEachIndexed { idxElement, element ->
            fun data() : SVGMeshData = SVGMeshData(paths, idxPath, idxElement)
            fun revp() : SVGMeshData = SVGMeshData.reverse()
            fun line(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>) {
                m.plus(v0, v1, ce(data(), v0, v1), ce(revp(), v1, v0))
            }
            fun line(v:VectorF) {
                val target = if(element.relative) current!!.v+v else v
                val vert = m.add(target, cv(data(), v))
                line(current!!, vert)
                current=vert
            }
            fun lines(coords:Iterable<VectorF>) {
                var start = current!!
                coords.forEach {
                    val target = if(element.relative) start.v+it else it
                    current = m.add(target, cv(data(), target))
                    line(start, current!!)
                    start = current!!
                }
            }
            when(element) {
                is SVGMove -> {
                    assert(idxElement==0)
                    val pos :VectorF= element.coords.first()
                    current = m.add(pos, cv(data(), pos))
                    val tail = element.coords.drop(1)
                    lines(tail)
                }
                is SVGLine -> {
                    lines(element.coords)
                }
                is SVGHorizontal -> {
                    lines(element.coords)
                }
                is SVGVertical -> {
                    lines(element.coords)
                }
                is SVGCubic -> {
                    for(i in 0..element.coords.count()-1 step 3) {
                        val pos = if(element.relative) current!!.v+element.coords.elementAt(i+2) else element.coords.elementAt(i+2)
                        line(pos)
                    }
                }
                is SVGSmoothCubic -> {
                    for(i in 0..element.coords.count()-1 step 2) {
                        val pos = if(element.relative) current!!.v+element.coords.elementAt(i+1) else element.coords.elementAt(i+1)
                        line(pos)
                    }
                }
                is SVGQuad -> {
                    for(i in 0..element.coords.count()-1 step 2) {
                        val pos = if(element.relative) current!!.v+element.coords.elementAt(i+1) else element.coords.elementAt(i+1)
                        line(pos)
                    }
                }
                is SVGSmoothQuad -> {
                    lines(element.coords)
                }
            }
        }
    }
    return m
}


abstract class SVGPathElement(val id:Char, val coords:Iterable<VectorF>, val relative:Boolean=id.isLowerCase())
object  SVGNoop : SVGPathElement(' ', emptyList(), false)
class SVGClose(id:Char) : SVGPathElement(id, emptyList())
class SVGMove(id:Char, coords:Iterable<VectorF>) : SVGPathElement(id, coords)
open class SVGLine(id:Char, coords:Iterable<VectorF>) : SVGPathElement(id, coords)
class SVGHorizontal(id:Char, coords:Iterable<VectorF>) : SVGLine(id, coords)
class SVGVertical(id:Char, coords:Iterable<VectorF>) : SVGLine(id, coords)
open class SVGCurve(op:Char, coords:Iterable<VectorF>) : SVGPathElement(op, coords)
class SVGCubic(id:Char, coords:Iterable<VectorF>) : SVGCurve(id, coords) {init {assert(coords.count()%3==0)}}
class SVGSmoothCubic(id:Char, coords:Iterable<VectorF>) : SVGCurve(id, coords) {init {assert(coords.count()%2==0)}}
class SVGQuad(id:Char, coords:Iterable<VectorF>) : SVGCurve(id, coords)
class SVGSmoothQuad(id:Char, coords:Iterable<VectorF>) : SVGCurve(id, coords)
class SVGArcElement(val arcs:Iterable<SVGArcDef>, coords:Iterable<VectorF>, relative:Boolean) : SVGPathElement('a', coords, relative)
class SVGArcDef(val rx:Float, val ry:Float, large:Boolean, sweep:Boolean)
class SVGPath(val elements:List<SVGPathElement>) {
    override fun toString(): String = elements.map { "${it.id}(${it.coords.count()})" }.join(" ", "p: ", ".")
}

fun parseSVGPath(d:String) : Iterable<SVGPath> {
    val log = LoggerFactory.getLogger(javaClass<SVGPathParser>())
    val parser = Parboiled.createParser(SVGPathParser::class.java)
    val res = BasicParseRunner<Iterable<SVGPath>>(parser.paths()).run(d)
    if(res.parseErrors.size()>0) {
        res.parseErrors.forEach {
            log.error("${it.getErrorMessage()}")

        }
        throw IllegalStateException()
    }
    return res.resultValue
}

open class SVGPathParser() : org.parboiled.BaseParser<Any>() {
    open fun ws() : Rule = AnyOf(" \t\n\r${0x20.toChar()}${0x9.toChar()}${0xD.toChar()}${0xA.toChar()}")
    open fun mandsep() : Rule = FirstOf(OneOrMore(ws()), Sequence(ZeroOrMore(ws()), ',', ZeroOrMore(ws())))
    open fun digits() : Rule = CharRange('0', '9')
    open fun sign() : Rule = AnyOf("+-")
    open fun exp() : Rule = Sequence(AnyOf("eE"), Optional(sign()), OneOrMore(digits()))
    open fun ufloat() : Rule = FirstOf(
            Sequence(OneOrMore(digits()), '.', OneOrMore(digits()), Optional(exp())),
            Sequence('.', OneOrMore(digits()), Optional(exp())),
            Sequence(OneOrMore(digits()), Optional(exp())),
            OneOrMore(digits())
    )
    open fun unsigned() : Rule = Sequence(ufloat(), push(match().toFloat()))
    open fun float() : Rule = Sequence(FirstOf(
            Sequence(sign(), ufloat()), ufloat()),
            push(match().toFloat()))

    //match initial float pair (no ',' optional ws
    open fun floatPair() : Rule = Sequence(float(), FirstOf(Sequence(mandsep(), unsigned()), Sequence(Optional(mandsep()), float())),
            push(VectorF(pop(1) as Float, pop() as Float, 0)))
    //match pair after initial sep mandaotiry if not neg
    open fun nextFloatPair() : Rule = Sequence(
            FirstOf(Sequence(mandsep(), unsigned()), Sequence(Optional(mandsep()), float())),
            FirstOf(Sequence(mandsep(), unsigned()), Sequence(Optional(mandsep()), float())),
            push(VectorF(pop(1) as Float, pop() as Float, 0)))

    //for 1 n* coords floatPairList
    //for 1 2 n* coords firstPair tailPairList
    //for 1 2 3 n* coords: firstPair headPair tailPairList
    //matches one initial pair and * following
    open fun floatPairList() : Rule = Sequence(Optional(ws()), floatPair(), push(listOf(pop())),
            ZeroOrMore(Sequence(nextFloatPair(), push((pop(1) as List<*>)+pop()))))
    //matches one non=initial pair and * following
    open fun tailPairList() : Rule = Sequence(nextFloatPair(), push((pop(1) as List<*>)+pop()),
            ZeroOrMore(Sequence(nextFloatPair(), push((pop(1) as List<*>)+pop()))))
    //matches one pair and pushes list
    open fun firstPair() : Rule = Sequence(Optional(ws()), floatPair(), push(listOf(pop())))
    open fun headPair() : Rule = Sequence(nextFloatPair(), push((pop(1) as List<*>)+pop()))
    open fun firstNum(dim:Int) : Rule = Sequence(ZeroOrMore(ws()), float(), push(VectorF(3) {if(it==dim) pop() as Float else 0f}))
    open fun nextNum(dim:Int) : Rule = FirstOf(
            Sequence(ZeroOrMore(ws()), float(), push(VectorF(3) {if(it==dim) pop() as Float else 0f})),
            Sequence(mandsep(), unsigned(), push(VectorF(3) {if(it==dim) pop() as Float else 0f}))
    )
    open fun numlist(dim:Int) : Rule = Sequence(
            Sequence(firstNum(dim), push(listOf(pop()))),
            ZeroOrMore(Sequence(nextNum(dim), push((pop(1) as List<*>)+pop())))
    )


    open fun move() : Rule = Sequence(AnyOf("mM"), push(matchedChar()), floatPairList(), push(SVGMove(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun line() : Rule = Sequence(AnyOf("lL"), push(matchedChar()), floatPairList(), push(SVGLine(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun hline() : Rule = Sequence(AnyOf("hH"), push(matchedChar()), numlist(0), push(SVGHorizontal(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun vline() : Rule = Sequence(AnyOf("vV"), push(matchedChar()), numlist(1), push(SVGVertical(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun cubic() : Rule = Sequence(AnyOf("cC"), push(matchedChar()), firstPair(), headPair(), tailPairList(),
             push(SVGCurve(pop(1) as Char, pop() as Iterable<VectorF>)))
    open fun smooth() : Rule = Sequence(AnyOf("sS"), push(matchedChar()), floatPairList(), push(SVGSmoothCubic(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun quad() : Rule = Sequence(AnyOf("qQ"), push(matchedChar()), floatPairList(), push(SVGQuad(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun qsmooth() : Rule = Sequence(AnyOf("tT"), push(matchedChar()), floatPairList(), push(SVGSmoothQuad(pop(1) as Char,
            pop() as Iterable<VectorF>)))
    open fun close() : Rule = Sequence(AnyOf("zZ"), push(SVGClose(matchedChar())))
    open fun draws() : Rule = OneOrMore(FirstOf(
            Sequence(line(), push((pop(1) as List<*>)+pop())),
            Sequence(cubic(), push((pop(1) as List<*>)+pop())),
            Sequence(smooth(), push((pop(1) as List<*>)+pop())),
            Sequence(quad(), push((pop(1) as List<*>)+pop())),
            Sequence(qsmooth(), push((pop(1) as List<*>)+pop())),
            Sequence(hline(), push((pop(1) as List<*>)+pop())),
            Sequence(vline(), push((pop(1) as List<*>)+pop()))
    ))
    open fun closed() : Rule = Sequence(close(), push((pop(1) as List<*>)+pop()), debug("@@@@@closed"))
    open fun path() : Rule = Sequence(Sequence(move(), push(listOf(pop())), debug("###pmove")),
            ZeroOrMore(draws()), Optional(closed()),
            ZeroOrMore(ws()), debug("+++++pathend"), push(SVGPath(pop() as List<SVGPathElement>)))
    open fun pathEnd() : Rule = Sequence(path(), debug("apapth"), push((pop(1) as List<*>)+pop()), ZeroOrMore(ws()))
    open fun paths() : Rule = Sequence(push(listOf<SVGPath>()),
            Sequence(
                    ZeroOrMore(ws()),
            ZeroOrMore(pathEnd(), debug("after pend")),
                    ZeroOrMore(ws()),
            debug("EOI")
    ))

    open fun debug(s:String="") : Boolean {
        val vs = getContext().getValueStack()
        println(s)
        vs.forEachIndexed {
            idx, it ->
            println("$idx: $it")
        }
        return true
    }

}

fun main(args: Array<String>) {
    //val p : SVGPathParserPhew = Parboiled.createParser(javaClass<SVGPathParserPhew>())
    val xml = """
    <glyph unicode="&#xf00a;" horiz-adv-x="1792" d="M512 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 288v-192q0 -40 -28 -68t-68 -28h-320 q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28 h320q40 0 68 -28t28 -68zM1792 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 800v-192 q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68z" />
    """

    val path1 = "M512 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 288v-192q0 -40 -28 -68t-68 -28h-320 q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28 h320q40 0 68 -28t28 -68zM1792 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 800v-192 q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68z"
    val path2 = "M512 288,12 20l 12 20 -.222e3 222v-.11-22 -.3,4"
    val svgp = Parboiled.createParser(javaClass<SVGPathParser>())
    val res = TracingParseRunner<List<SVGPathElement>>(svgp.path()).run(path2)
    //val res = TracingParseRunner<List<SVGPathElement>>(svgp.paths()).run("M-.2e-3,.2222 12 -3.154")
    println(ParseTreeUtils.printNodeTree(res))
}
