package ch.passenger.kotlin.graphics.mesh.svg

import ch.passenger.kotlin.graphics.math.VectorF
import org.parboiled.Parboiled
import org.parboiled.buffers.DefaultInputBuffer
import org.parboiled.buffers.InputBuffer
import org.parboiled.common.StringUtils
import org.parboiled.errors.ParseError
import org.parboiled.parserunners.BasicParseRunner
import org.parboiled.parserunners.ParseRunner
import org.parboiled.parserunners.TracingParseRunner
import org.parboiled.support.ParseTreeUtils
import org.parboiled.support.ParsingResult
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import org.testng.AssertJUnit.*
import kotlin.reflect.jvm.java

/**
 * Created by svd on 10/05/2015.
 */
class ParserTests {
    val log = LoggerFactory.getLogger(javaClass<ParserTests>())
    val xml = """
    <glyph unicode="&#xf00a;" horiz-adv-x="1792" d="M512 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 288v-192q0 -40 -28 -68t-68 -28h-320 q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28 h320q40 0 68 -28t28 -68zM1792 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 800v-192 q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68z" />
    """

    val path1 = "M512 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 288v-192q0 -40 -28 -68t-68 -28h-320 q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM512 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 800v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28 h320q40 0 68 -28t28 -68zM1792 288v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1152 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 800v-192 q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68zM1792 1312v-192q0 -40 -28 -68t-68 -28h-320q-40 0 -68 28t-28 68v192q0 40 28 68t68 28h320q40 0 68 -28t28 -68z"
    val path2 = "M512 288,12 20l 12 20 -.222e3 222v-.11-22 -.3,4"
    val dollar = """
    M313 749l0.000976562 -84.9971c25.333 -1.33203 51.833 -5.49902 79.5 -12.5c27.667 -7 53.5 -19.833 77.5 -38.5l-48 -58c-16 12 -33.5 20.5 -52.5 25.5s-37.833 8.16699 -56.499 9.5v-222c23.333 -5.33301 45.833 -11.5 67.5 -18.5s41 -16.5 58 -28.5
s30.5 -27.5 40.5 -46.5s15 -42.5 15 -70.5c-0.000976562 -54 -16.334 -97.833 -49 -131.5c-32.667 -33.667 -76.667 -53.834 -132 -60.501v-93h-78v92c-36 3.33301 -69.5 11.666 -100.5 24.999s-60.167 32.666 -87.5 57.999l54 54
c19.333 -18.667 40.166 -32.834 62.499 -42.501s46.166 -16.167 71.499 -19.5v223c-24 5.33301 -46.833 11.833 -68.5 19.5s-41 18 -58 31s-30.5 29.333 -40.5 49s-15 44.5 -15 74.5c0 26.668 5.16699 50.335 15.5 71.001c10.333 20.668 24 38.335 41 53
c17 14.6709 36.5 26.3379 58.5 35.001c22 8.66992 44.333 15.0029 67 18.999v88h78zM416.001 204.003c0 13.333 -2.66699 24.832 -8 34.499s-12.833 17.834 -22.5 24.501s-20.667 12.334 -33 17.001s-25.5 8.66699 -39.5 12v-205c32 6 57.167 19.167 75.5 39.5
c18.334 20.333 27.501 46.166 27.5 77.499zM131.001 483.002c0 -14.667 2.66895 -27.167 8.00195 -37.5s12.833 -19.333 22.5 -27c9.66699 -7.66895 20.834 -14.002 33.5 -19c12.667 -5 26 -9.5 40 -13.5v200c-13.333 -3.33301 -26.333 -7.83301 -39 -13.5
s-23.834 -12.667 -33.501 -21c-9.66699 -8.33594 -17.334 -18.1689 -23.001 -29.5c-5.66699 -11.333 -8.5 -24.333 -8.5 -39z
"""

    fun<T> ParseRunner<T>.all(vararg input:String) : Iterable<ParsingResult<T>> {
        val res = arrayListOf<ParsingResult<T>>()
        input.forEach {
            res.add(run(it))
        }

        return res
    }
    Test
    fun testNumbers() {
        val parser = Parboiled.createParser(javaClass<SVGPathParser>())
        val ai = "1234546"
        val si = "-1231234"
        val uf = ".2233e-4"
        val sf = "-2.233e33"
        val res = TracingParseRunner<Any>(parser.float()).all(ai, si, uf, sf)
        assertEquals(res.count(), 4)
        assertEquals(ai.toFloat(), res.elementAt(0).resultValue)
        assertEquals(si.toFloat(), res.elementAt(1).resultValue)
        assertEquals(uf.toFloat(), res.elementAt(2).resultValue)
        assertEquals(sf.toFloat(), res.elementAt(3).resultValue)
    }
    Test
    fun testCoords() {
        val parser = Parboiled.createParser(javaClass<SVGPathParser>())
        val lists = array(
                "12 2", ".22, .33", "2,.33,-2-3 13 2"
        )
        val res = TracingParseRunner<Any>(parser.floatPairList()).all(*lists)
        assertEquals(res.count(), 3)
        res.forEachIndexed {
            idx, it ->
            assertEquals("${lists[idx]} failed: ${it.parseErrors}", 0, it.parseErrors.size())
            assertNotNull("result for ${lists[idx]} null", it.resultValue)
            assert(it.resultValue is List<*>, "${it.resultValue.javaClass}")
            val rl = it.resultValue as List<VectorF>
            log.debug("$rl")
        }
        val r0 = TracingParseRunner<Any>(parser.floatPairList()).run(lists[0])
        val lst = r0.resultValue as List<VectorF>
        assertEquals(1, lst.size())
        val v0 = lst[0]
        assertEquals(12f, v0.x)
        assertEquals(2f, v0.y)

        val r1 = TracingParseRunner<Any>(parser.floatPairList()).run(lists[1])
        val lst1 = r1.resultValue as List<VectorF>
        assertEquals(1, lst1.size())
        val v1 = lst1[0]
        assertEquals(.22f, v1.x)
        assertEquals(.33f, v1.y)

        val r2 = TracingParseRunner<Any>(parser.floatPairList()).run(lists[2])
        val lst2 = r2.resultValue as List<VectorF>
        assertEquals(3, lst2.size())
        val v20 = lst2[0]
        assertEquals(2f, v20.x); assertEquals(.33f, v20.y)
        val v21 = lst2[1]
        assertEquals(-2f, v21.x); assertEquals(-3f, v21.y)
        val v22 = lst2[2]
        assertEquals(13f, v22.x); assertEquals(2f, v22.y)
    }

    Test
    fun testMove() {
        val move1 = "M222 333"
        val svgp = Parboiled.createParser(javaClass<SVGPathParser>())
        val res = TracingParseRunner<SVGMove>(svgp.move()).run(move1)
        assert(res.resultValue is SVGMove)
        val m = res.resultValue

        assertEquals(1, m.coords.count())
        val v = m.coords.first()
        assertEquals(VectorF(222, 333, 0), v)
        log.debug(ParseTreeUtils.printNodeTree(res))
        val move2 = "M22.2,3.33-12-2,3 4"
        val res2 = TracingParseRunner<SVGMove>(svgp.move()).run(move2)
        assert(res2.resultValue is SVGMove)
        val m2 = res2.resultValue
        assertEquals(3, m2.coords.count())
        val vs = listOf(VectorF(22.2, 3.33), VectorF(-12, -2), VectorF(3, 4))
        chkVectors(vs, m2.coords)
    }


    fun chkVectors(vexp:Iterable<VectorF>, vres:Iterable<VectorF>) {
        assertEquals(vexp.count(), vres.count())
        vexp.zip(vres).forEach { it.first().forEachIndexed { i, fl -> fl == it.second[i] } }
    }


    Test
    fun testPath() {
        val p1 = "M2 3l4 5c12 2,.33 .44, .3 -.1,13.2 3.8s12 2,3 4h12 23 13v.4-.2e1 3 4"
        val parser = Parboiled.createParser(javaClass<SVGPathParser>())
        val r1 = TracingParseRunner<SVGPath>(parser.path()).run(p1)
        assertEquals(0, r1.parseErrors.size())
        val sp1 = r1.resultValue
        sp1.elements.forEachIndexed { i, p ->
            println("$i: ${p.id} $p")
            p.coords.forEachIndexed { i, v ->
                println("$i: $v")
            }
        }
    }

    fun dumpPath(sp1:SVGPath) {
        sp1.elements.forEachIndexed { i, p ->
            println("$i: ${p.id} $p")
            p.coords.forEachIndexed { i, v ->
                println("$i: $v")
            }
        }
    }
    Test
    fun testPaths() {
        val p1 = "M2 3l4 5c12 2,.33 .44, .3 -.1,13.2 3.8s12 2,3 4h12 23 13v.4-.2e1 3 4"
        val parser = Parboiled.createParser(javaClass<SVGPathParser>())
        val r1 = TracingParseRunner<Iterable<SVGPath>>(parser.paths()).run(path1)
        dumpErrors(r1.parseErrors)
        assertEquals(0, r1.parseErrors.size())
        val sp1 = r1.resultValue
        sp1.forEach { dumpPath(it) }
    }


    Test
    fun testMoveMore() {
        val move1 = "M222 333-2 3.2e3"
        val svgp = Parboiled.createParser(javaClass<SVGPathParser>())
        val res = BasicParseRunner<SVGPath>(svgp.path()).run(move1)
        assertNotNull(res.resultValue)
        assert(res.resultValue is SVGPath, "res: ${res.resultValue.javaClass}")
        val p = res.resultValue
        assert(res.resultValue.elements.size()==1)
        val m1 = res.resultValue.elements[0]
        assert(m1 is SVGMove)
        assertEquals(2, m1.coords.count())
        assertEquals(VectorF(222, 333, 0), m1.coords.first())
        log.debug(ParseTreeUtils.printNodeTree(res))
    }

    Test
    fun stupid() {
        val x = listOf(Unit)
        val f = (x.first())
        val kc = Unit::class
        javaClass<Unit>().getClasses().forEach { println("u: $it") }
        javaClass<Any>().getClasses().forEach { println("a: $it") }
        Unit::class.java.getClasses().forEach { println("u1: $it") }
        Any::class.java.getClasses().forEach { println("a1: $it") }
        Unit::class.javaClass.getClasses().forEach { println("u1: $it") }
        Any::class.javaClass.getClasses().forEach { println("a1: $it") }
        assert(javaClass<Any>().isAssignableFrom(javaClass<Unit>()))
    }

    Test
    fun paths() {
        val paths = """M3 2 4   1l2  3l3 4z M12 2 3 2z
        M4 1-4 3L23 3c1 2 3 4 5   6 7 8s12 2 13 4s.44 .22 .33 .44
        M0 0
        """
        val svgp = Parboiled.createParser(javaClass<SVGPathParser>())
        log.info("nlc: ${paths.filter { it=='\n' }.count()}")
        val res = BasicParseRunner<List<SVGPath>>(svgp.paths()).run(paths)
        if(res.parseErrors.size()>0) dumpErrors(res.parseErrors)
        assertNotNull(res.resultValue)
        assert(res.resultValue is Iterable<SVGPath>, "res: ${res.resultValue.javaClass}")
        val p = res.resultValue
        p.forEach { dumpPath(it) }
    }

    Test
    fun dollar() {
        val svgp = Parboiled.createParser(javaClass<SVGPathParser>())
        log.debug(dollar)
        log.info("nlc: ${dollar.filter { it=='\n' }.count()}")
        val res = BasicParseRunner<Iterable<SVGPath>>(svgp.paths()).run(dollar)
        if(res.parseErrors.size()>0) dumpErrors(res.parseErrors)
        assertNotNull(res.resultValue)
        assert(res.resultValue is Iterable<SVGPath>, "res: ${res.resultValue.javaClass}")
        val p = res.resultValue
        p.forEach { dumpPath(it) }

    }

    fun dumpErrors(errs:Iterable<ParseError>) {
        errs.forEach { log.error("${it.getErrorMessage()}: ${it.getInputBuffer().extract(it.getStartIndex(), it.getEndIndex())}") }
    }

}