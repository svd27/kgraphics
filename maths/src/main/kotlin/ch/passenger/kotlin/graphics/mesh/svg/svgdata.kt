package ch.passenger.kotlin.graphics.mesh.svg

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.geometry.CubicBezier
import ch.passenger.kotlin.graphics.geometry.Curve
import ch.passenger.kotlin.graphics.geometry.QuadBezier
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh
import ch.passenger.kotlin.graphics.mesh.*
import ch.passenger.kotlin.graphics.util.logging.d
import ch.passenger.kotlin.graphics.util.logging.t
import ch.passenger.kotlin.graphics.util.logging.w
import ch.qos.logback.classic.Level
import javafx.scene.canvas.GraphicsContext
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
    var pred : SVGMeshData?
    val origin : VectorF
    val target:VectorF
    companion object {
        open data class MD(override val paths: Iterable<SVGPath>, override val pathIdx: Int, override val idxPathelement: Int, override val coordOffset: Int, override var pred: SVGMeshData?) : SVGPathMeshData
        open data class MDCurve(paths: Iterable<SVGPath>, pathIdx: Int, idxPathelement: Int, coordOffset: Int, pred: SVGMeshData?) : MD(paths, pathIdx, idxPathelement, coordOffset, pred), SVGCurveMeshData {
            override val curve: Curve
                get() {
                    val pe = pathElement
                    when (pathElement) {
                        is SVGCubic -> {
                            var start = origin
                            val i = coordOffset
                            if (pe.relative) {
                                val a = start
                                val b = a + pe.coords.elementAt(i)
                                val c = a + pe.coords.elementAt(i + 1)
                                val d = a + pe.coords.elementAt(i + 2)
                                return CubicBezier(a, d, listOf(b, c))
                            } else {
                                val a = start
                                val b = pe.coords.elementAt(i)
                                val c = pe.coords.elementAt(i + 1)
                                val d = pe.coords.elementAt(i + 2)
                                return CubicBezier(a, d, listOf(b, c))
                            }

                        }
                        is SVGSmoothCubic -> {
                            val prev = pred
                            if (prev is SVGCurveMeshData && (prev.pathElement is SVGCubic || prev.pathElement is SVGSmoothCubic)) {
                                val cp1 = prev.curve.cps.last().reflect(origin)
                                val a = origin
                                val c = if (pe.relative) origin + pe.coords.elementAt(coordOffset) else pe.coords.elementAt(coordOffset)
                                val d = if (pe.relative) origin + pe.coords.elementAt(coordOffset) else pe.coords.elementAt(coordOffset + 1)
                                return CubicBezier(a, d, listOf(cp1, c))
                            } else throw UnsupportedOperationException()
                        }
                        is SVGQuad -> {
                            val b = if (pe.relative) origin + pe.coords.elementAt(coordOffset) else pe.coords.elementAt(coordOffset)
                            val c = if (pe.relative) origin + pe.coords.elementAt(coordOffset + 1) else pe.coords.elementAt(coordOffset + 1)
                            return QuadBezier(origin, c, listOf(b))
                        }
                        is SVGSmoothQuad -> {
                            val prev = pred
                            if (prev is SVGCurveMeshData && (prev.pathElement is SVGQuad || prev.pathElement is SVGSmoothQuad)) {
                                val cp1 = prev.curve.cps.first().reflect(origin)
                                return QuadBezier(origin, target, listOf(cp1))
                            } else throw UnsupportedOperationException()
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
        }

        fun invoke(paths: Iterable<SVGPath>, path: Int, idxPathelement: Int, offset: Int, pred: SVGMeshData?): SVGMeshData {
            val pe = paths.elementAt(path).elements[idxPathelement]
            return when (pe) {
                is SVGQuad -> MDCurve(paths, path, idxPathelement, offset, pred)
                is SVGSmoothQuad -> MDCurve(paths, path, idxPathelement, offset, pred)
                is SVGCubic -> MDCurve(paths, path, idxPathelement, offset, pred)
                is SVGSmoothCubic -> MDCurve(paths, path, idxPathelement, offset, pred)
                else -> MD(paths, path, idxPathelement, offset, pred)
            }
        }

        fun reverse(): SVGMeshData = object : SVGReversePath {
            override var pred: SVGMeshData?
                get() = null
                set(value) {
                }
            override val origin: VectorF
                get() = VectorF(0, 0, 0)
            override val target: VectorF
                get() = origin
        }

        fun inserted(): SVGInserted = object : SVGInserted {
            override var pred: SVGMeshData?
                get() = null
                set(value) {
                }
            override val origin: VectorF
                get() = VectorF(0, 0, 0)
            override val target: VectorF
                get() = origin
        }
    }

}
trait SVGInserted : SVGMeshData
trait SVGReversePath : SVGMeshData
trait SVGPathMeshData : SVGMeshData {
    val paths:Iterable<SVGPath>
    val pathIdx: Int
    val idxPathelement : Int
    val path :SVGPath get() = paths.elementAt(pathIdx)
    val pathElement:SVGPathElement get() = paths.elementAt(pathIdx).elements[idxPathelement]
    val coordOffset : Int
    override val origin : VectorF get() =
        if(pathElement is SVGMove) pathElement.coords.first()
        else pred!!.target

    override val target : VectorF get() {
        if(pathElement.relative) {
            when(pathElement) {
                is SVGMove -> {
                    return origin
                }
                is SVGLine -> {
                    return origin+pathElement.coords.elementAt(coordOffset)
                }
                is SVGCubic -> {
                    return origin+pathElement.coords.elementAt(coordOffset+2)
                }
                is SVGSmoothCubic -> {
                    return origin+pathElement.coords.elementAt(coordOffset+1)
                }
                is SVGQuad -> {
                    return origin+pathElement.coords.elementAt(coordOffset+1)
                }
                is SVGSmoothQuad -> {
                    return origin+pathElement.coords.elementAt(coordOffset)
                }
                is SVGClose -> {
                    var apred = pred!!
                    while(apred.pred!=null) apred = apred.pred!!
                    assert(apred is SVGPathMeshData && apred.pathElement is SVGMove )
                    return apred.origin
                }
                else -> throw IllegalStateException("$pathElement ${pathElement.javaClass}")
            }
        } else {
            when(pathElement) {
                is SVGMove -> {
                    return origin
                }
                is SVGLine -> {
                    return pathElement.coords.elementAt(coordOffset)
                }
                is SVGCubic -> {
                    return pathElement.coords.elementAt(coordOffset+2)
                }
                is SVGSmoothCubic -> {
                    return pathElement.coords.elementAt(coordOffset+1)
                }
                is SVGQuad -> {
                    return pathElement.coords.elementAt(coordOffset+1)
                }
                is SVGSmoothQuad -> {
                    return pathElement.coords.elementAt(coordOffset)
                }
                is SVGClose -> {
                    var apred = pred!!
                    while(apred.pred!=null) apred = apred.pred!!
                    assert(apred is SVGPathMeshData && apred.pathElement is SVGMove )
                    return apred.origin
                }
                else -> throw IllegalStateException("$pathElement ${pathElement.javaClass}")
            }
        }
    }
}

trait SVGCurveMeshData : SVGPathMeshData {
    val curve : Curve
}


fun<H:SVGMeshData,V:SVGMeshData,F:SVGMeshData>
        createMesh(paths:Iterable<SVGPath>)  : Mesh<H,V,F> {
    val log = LoggerFactory.getLogger("SVG")
    if(log is ch.qos.logback.classic.Logger) {
        log.setLevel(Level.TRACE)
    }

    val min = MutableVectorF(3) { Float.POSITIVE_INFINITY}
    val max = MutableVectorF(3) { Float.NEGATIVE_INFINITY}
    paths.forEach { it.elements.forEach { it.coords.forEach { it().forEachIndexed { i, fl ->
        if(min[i]>fl) min[i] = fl; if(max[i]<fl) max[i] = fl
    } } } }
    val sub = max-min
    val dim = Math.max(sub.x, sub.y)
    val tolerance = dim/100000
    log.d{"tolerance: $tolerance"}

    class PContext() {
        var idxPath:Int=0
        var pred : SVGMeshData? = null
        var idxElement = 0
        var offset:Int = 0


        fun data() : H {val data =SVGMeshData(paths, idxPath, idxElement, offset, pred); pred=data; return data as H}
        fun vdata() : V {val data = SVGMeshData(paths, idxPath, idxElement, offset, pred) as V; if(pred==null) pred=data; return data}
        fun revp() : H = SVGMeshData.reverse() as H
    }
    val df : MeshDataFactory<H, V, F, PContext> = MeshDataFactory.from(PContext(),
            {
                (dataFactory.context as PContext).vdata()
            },
            {
                v0, v1 -> (dataFactory.context as PContext).data()
            },
            {
                e, p -> SVGMeshData.reverse() as F
            })
    val m = Mesh<H,V,F>(AlignedCube(VectorF(-1, -1, -1), VectorF(1, 1, 1)), df)

    paths.forEachIndexed { idxp, path ->
        fun context() : PContext = m.dataFactory.context as PContext
        context().idxPath = idxp
        var current : Vertex<H,V,F>? = null
        var vstart : Vertex<H,V,F>? = null
        path.elements.forEachIndexed { idxe, element ->
            context().idxElement = idxe
            context().offset = 0

            fun vert(v:VectorF) : Vertex<H,V,F> {
                if(v in m) {
                    log.t{"$v already in using ${m[v]}"}
                    return m[v]!!
                } else {
                    val cube = AlignedCube.around(v, tolerance/2)
                    val verts = m.findVertices(cube)
                    log.t{"vertices in $cube: $verts"}
                    if(verts.count()==0)
                      return m.add(v)
                    else {
                        log.w{"$v ignored using existing ${verts}"}
                        return verts.sortBy {VectorF.distance(v, it.v)}.first()
                    }
                }
            }
            fun line(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>) {
                log.t{ "edge $v0->$v1"}
                m.add(v0, v1)
            }
            fun line(v:VectorF) {
                log.t(element, current?:"") {"->$element $current ${element.svg}"}
                val target = if(element.relative) current!!.v+v else v
                val vert = vert(target)
                if(current!=vert)
                line(current!!, vert)
                else log.w {"ignored empty edge ${current}->${vert} ${element} ${element.svg}"}
                current=vert
                log.t(element, current?:"") {"<-$element $current"}
            }
            fun lines(coords:Iterable<VectorF>) {
                log.t(element, current?:"") {">>$element $current ${element.svg}"}
                var start = current!!
                coords.forEach {
                    val target = if(element.relative) current!!.v+it else it
                    current = vert(target)
                    if(current!=start)
                    line(start, current!!)
                    else log.w {"ignored empty edge ${start}->${current} ${element} ${element.svg}"}
                    start = current!!
                    context().offset++
                }
                log.t(element, current?:"") {"<<$element $current ${element.svg}"}
            }
            context().offset = 0
            when(element) {
                is SVGMove -> {
                    assert(context().idxElement==0)
                    val pos :VectorF= element.coords.first()
                    current = m.add(pos)
                    log.t {"M at $current"}
                    vstart = current
                    val tail = element.coords.drop(1)
                    if(tail.count()>0) lines(tail)
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
                        context().offset+=i
                        val pos = element.coords.elementAt(i+2)
                        line(pos)
                    }
                }
                is SVGSmoothCubic -> {
                    for(i in 0..element.coords.count()-1 step 2) {
                        context().offset+=i
                        val pos = element.coords.elementAt(i+1)
                        line(pos)
                    }
                }
                is SVGQuad -> {
                    for(i in 0..element.coords.count()-1 step 2) {
                        context().offset+=i
                        val pos = element.coords.elementAt(i+1)
                        line(pos)
                    }
                }
                is SVGSmoothQuad -> {
                    lines(element.coords)
                }
                is SVGClose -> {
                    if(current!=vstart) {
                        log.d{"path close distance ${VectorF.distance(current!!.v, vstart!!.v)}"}
                        line(current!!, vstart!!)
                    }
                }
            }
        }
    }
    m.dataFactory = MeshDataFactory.from<H,V,F,Unit>(Unit, {SVGMeshData.inserted() as V},
            {v0, v1-> SVGMeshData.inserted() as H},
            {e, p -> SVGMeshData.inserted() as F})

    return m
}


abstract class SVGPathElement(val id:Char, val coords:Iterable<VectorF>, val svg:String, val relative:Boolean=id.isLowerCase()) {
    override fun toString(): String = "SVGPathElement: $id(${coords.count()})"
}
object  SVGNoop : SVGPathElement(' ', emptyList(), "", false)
class SVGClose(id:Char) : SVGPathElement(id, emptyList(), "")
class SVGMove(id:Char, coords:Iterable<VectorF>, svg:String) : SVGPathElement(id, coords, svg)
open class SVGLine(id:Char, coords:Iterable<VectorF>, svg:String) : SVGPathElement(id, coords, svg)
class SVGHorizontal(id:Char, coords:Iterable<VectorF>, svg:String) : SVGLine(id, coords, svg)
class SVGVertical(id:Char, coords:Iterable<VectorF>, svg:String) : SVGLine(id, coords, svg)
open class SVGCurve(op:Char, coords:Iterable<VectorF>, svg:String) : SVGPathElement(op, coords, svg)
class SVGCubic(id:Char, coords:Iterable<VectorF>, svg:String) : SVGCurve(id, coords, svg) {init {assert(coords.count()%3==0)} }
class SVGSmoothCubic(id:Char, coords:Iterable<VectorF>, svg:String) : SVGCurve(id, coords, svg) {init {assert(coords.count()%2==0)}}
class SVGQuad(id:Char, coords:Iterable<VectorF>, svg:String) : SVGCurve(id, coords, svg)
class SVGSmoothQuad(id:Char, coords:Iterable<VectorF>, svg:String) : SVGCurve(id, coords, svg)
class SVGArcElement(val arcs:Iterable<SVGArcDef>, coords:Iterable<VectorF>, svg:String) : SVGPathElement('a', coords, svg)
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
            pop() as Iterable<VectorF>, match())))
    open fun line() : Rule = Sequence(AnyOf("lL"), push(matchedChar()), floatPairList(), push(SVGLine(pop(1) as Char,
            pop() as Iterable<VectorF>, match())))
    open fun hline() : Rule = Sequence(AnyOf("hH"), push(matchedChar()), numlist(0), push(SVGHorizontal(pop(1) as Char,
            pop() as Iterable<VectorF>, match())))
    open fun vline() : Rule = Sequence(AnyOf("vV"), push(matchedChar()), numlist(1), push(SVGVertical(pop(1) as Char,
            pop() as Iterable<VectorF>, match())))
    open fun cubic() : Rule = Sequence(AnyOf("cC"), push(matchedChar()), firstPair(), headPair(), tailPairList(),
             push(SVGCubic(pop(1) as Char, pop() as Iterable<VectorF>, match())))
    open fun smooth() : Rule = Sequence(AnyOf("sS"), push(matchedChar()), floatPairList(), push(SVGSmoothCubic(pop(1) as Char,
            pop() as Iterable<VectorF>, match())))
    open fun quad() : Rule = Sequence(AnyOf("qQ"), push(matchedChar()), floatPairList(), push(SVGQuad(pop(1) as Char,
            pop() as Iterable<VectorF>, match())))
    open fun qsmooth() : Rule = Sequence(AnyOf("tT"), push(matchedChar()), floatPairList(), push(SVGSmoothQuad(pop(1) as Char,
            pop() as Iterable<VectorF>, match())))
    open fun close() : Rule = Sequence(ZeroOrMore(ws()), AnyOf("zZ"), push(SVGClose(matchedChar())), ZeroOrMore(ws()))
    open fun draws() : Rule = OneOrMore(draw())
    open fun draw() : Rule = Sequence(ZeroOrMore(ws()),
            FirstOf(line(), cubic(), smooth(), quad(), qsmooth(), hline(), vline()),
            push((pop(1) as List<*>)+pop()), ZeroOrMore(ws())
    )
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
        val log = LoggerFactory.getLogger("PARSE")
        val vs = getContext().getValueStack()
        log.debug(s)
        log.debug(getContext().getInputBuffer().extract(getContext().getMatchRange()))
        vs.forEachIndexed {
            idx, it ->
            log.debug("$idx: $it")
        }

        return true
    }

}
