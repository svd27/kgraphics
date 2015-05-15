package ch.passenger.kotlin.graphics.mesh

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.util.collections.Hierarchy
import ch.passenger.kotlin.graphics.util.logging.d
import ch.passenger.kotlin.graphics.util.logging.e
import ch.passenger.kotlin.graphics.util.logging.t
import org.slf4j.LoggerFactory
import java.util.*

/**
 * svd coded this on 13/05/2015.
 */
class MeshOperations<H,V,F>(val mesh:Mesh<H,V,F>, val X:Int=0, val Y:Int=1) {
    val log = LoggerFactory.getLogger(this.javaClass)
    //cf http://geomalgorithms.com/a03-_inclusion.html
    fun wn(v:Vertex<H,V,F>, e:HalfEdge<H,V,F>) : Int {
        fun isLeft(v:Vertex<H,V,F>, e:HalfEdge<H,V,F>) : Int {
            val p0 = e.origin.v; val p1 = e.destination.v
            val p2 = v.v
            val left = Math.signum((
                    p1[X] - p0[X]) * (p2[Y] - p0[Y]) - (p2[X] - p0[X]) * (p1[Y] - p0[Y])
            )
            log.t{"isLeft: $v of $e"}
            return left.toInt()
        }
        var wn = 0
        e().forEach {
            val v1 = it.origin.v; val v2 = it.destination.v
            val p = v.v
            if(v1[Y]<=p[Y]) {
                if(v2[Y]>p[Y]) {
                    if(isLeft(v, it)>0) ++wn
                }
            } else {
                if(v2[Y] <= p[Y]) {
                    if(isLeft(v, it)<0) --wn
                }
            }
        }
        log.t {"wn $v -> $e"}
        return wn
    }

    fun cycles() : Set<HalfEdge<H,V,F>> {
        val cycles = hashSetOf<HalfEdge<H,V,F>>()
        val done = hashSetOf<HalfEdge<H,V,F>>()
        mesh.edges.forEach {
            if(it !in done) {
                if(it.cycle) cycles.add(it)
                it().forEach { done add it }
            }
        }
        return cycles
    }


    fun containment() : Hierarchy<HalfEdge<H,V,F>> {
        val hierarchy = Hierarchy<HalfEdge<H, V, F>>()
        val insides = cycles().filter { it.insideLooking }
        insides.forEach {
            candidate ->
            if(candidate !in hierarchy) hierarchy add candidate
            insides.filter { it!=candidate }.forEach {
                container ->
                if(candidate().map { it.origin }.all { wn(it, container) != 0 }) {
                    hierarchy.isIn(candidate, container)
                }
            }
        }


        return hierarchy
    }




    fun createFaces() {
        val cont = containment()
        cont.collect(0) { i:Int, it ->
            val edge = it
            if(!edge.left.properFace && edge.insideLooking) {
                mesh.linkFace(edge, edge.left)
            }
            if(edge.left.properFace && !edge.insideLooking) {
                mesh.unlinkFace(edge.left)
            }
            val twin = edge.twin
            if(twin.left.properFace && !twin.insideLooking) {
                mesh.unlinkFace(twin.left)
            }
            i+1
        }
        cont.collect(0) { i, edge ->
            if(!edge.left.properFace && edge.insideLooking) {
                mesh.linkFace(edge, edge.left)
            }
            if(edge.left.properFace && !edge.insideLooking) {
                mesh.unlinkFace(edge.left)
            }
            if(edge.left.properFace && i%2!=0) edge.left.hole=true
            val twin = edge.twin
            if(twin.left.properFace && !twin.insideLooking) {
                mesh.unlinkFace(twin.left)
            }
            i+1
        }
    }

    fun closeHoles() {
        fun cl(v1:Vertex<H,V,F>, v2:Vertex<H,V,F>) : LineSegment =
                LineSegment.create(VectorF(v1.v[X], v1.v[Y], 0), VectorF(v2.v[X], v2.v[Y], 0))
        val containment = containment()
        val faceset = mesh.faces.toList()
        faceset.forEach {
            if(it.hole && it.edge.rim && !doubleCloseHole(it, containment)) {
                val face = it
                log.d{"trying close $face"}
                val maxl = containment.maxlevel
                val containers = arrayListOf<HalfEdge<H,V,F>>()
                containment.leveled { i, edge ->  if(i<maxl) containers add edge}

                val mine = it.edge().map { it.origin }.sortBy {it.v[X]}.reverse()
                //val cycles = mesh.cycles { face.edge !in it() && it.insideLooking && it in containers}

                containers.takeWhile {
                    log.d{"trying container $face"}
                    val outside = it
                    var rest = mine
                    var start = mine.first()
                    var done = false
                    while(rest.size()>0 && !done) {
                        start = rest.first()
                        log.d{"start: $start"}
                        rest = rest.drop(1).reverse()
                        done = outside().map { it.origin }.any {
                            ov ->
                            log.d{"ov: $ov"}
                            val hz = AlignedCube.ensureVolume(start.v, ov.v).scale(.2f)
                            val l1 = cl(start, ov)
                            if(mesh.edges.none { cl(it.origin, it.destination).intersects2D(l1) {
                                other, newedge, inter ->
                                if(start.equals(start.v, other.start) || start.equals(start.v, other.end) ||
                                start.equals(ov.v, other.start) || start.equals(ov.v, other.end)) {
                                    inter.tsegment > 0f && inter.tsegment < 1f && inter.tray > 0f && inter.tray<1f
                                } else true
                            } }) {
                                val ehole = start().map {it.twin}.first {!it.insideLooking}
                                val econtainer = ov().first {it in outside && it.insideLooking}
                                log.d{"found connection: $start->$ov"}
                                mesh.bridge(ehole, econtainer)
                                log.d{"done with ${face}"}
                                true
                            } else false
                        }
                    }

                    !done
                }
            }
        }
    }

    fun doubleCloseHole(f:Face<H,V,F>, h:Hierarchy<HalfEdge<H,V,F>>)  : Boolean {
        val maxl = h.maxlevel
        fun cl(v1:Vertex<H,V,F>, v2:Vertex<H,V,F>) : LineSegment =
                LineSegment.create(VectorF(v1.v[X], v1.v[Y], 0), VectorF(v2.v[X], v2.v[Y], 0))
        val mine = f.edge().sortBy {it.destination.v[X]}
        val containers = arrayListOf<HalfEdge<H,V,F>>()
        h.leveled { i, edge ->  if(i<maxl) containers add edge}
        var closed = false
        fun match(inner:HalfEdge<H,V,F>, outer:HalfEdge<H,V,F>) : Boolean {
            val  start = inner.destination
            val end = outer.origin
            val l1 = cl(start, end)
            val hz = AlignedCube.ensureVolume(start.v, end.v).scale(.2f)
            return mesh.findEdges(hz).none {
                cl(it.origin, it.destination).intersects2D(l1) {
                    other, newedge, inter ->
                    if(start.equals(start.v, other.start) || start.equals(start.v, other.end) ||
                            start.equals(end.v, other.start) || start.equals(end.v, other.end)) {
                        inter.tsegment > 0f && inter.tsegment < 1f && inter.tray > 0f && inter.tray<1f
                    } else true
                }
            }
        }
        containers.forEach {
            val outeredges = hashSetOf<HalfEdge<H,V,F>>()
            outeredges.addAll(it())
            var rest = mine
            var c1 : Pair<HalfEdge<H,V,F>,HalfEdge<H,V,F>>? = null
            while(rest.size()>0 && c1==null ) {
                val inner = rest.first()
                val o1 = outeredges.firstOrNull {
                    match(inner, it)
                }
                if(o1!=null) c1 = inner to o1
                rest = rest.drop(1)
            }
            if(c1!=null) {
                rest = mine.filter { it!=c1!!.first }
                outeredges.remove(c1!!.second)
                var c2 : Pair<HalfEdge<H,V,F>,HalfEdge<H,V,F>>? = null
                while(rest.size()>0 && c2==null ) {
                    val inner = rest.first()
                    val o1 = outeredges.firstOrNull {
                        match(inner, it)
                    }
                    if(o1!=null) c2 = inner to o1
                    rest = rest.drop(1)
                }
                if(c2!=null) {
                    log.d{"close pair $c1 $c2"}
                    mesh.bridge(c1!!.first, c1!!.second)
                    mesh.bridge(c2!!.first, c2!!.second)
                }
            }
        }
        return false
    }

    enum class Monotonie {
        START SPLIT MERGE END NORM
    }

    val monotonyComparator : Comparator<Vertex<H,V,F>> = object: Comparator<Vertex<H, V, F>> {
        override fun compare(o1: Vertex<H, V, F>?, o2: Vertex<H, V, F>?): Int {
            if(o1==null) return -1
            if(o2==null) return 1
            val y1 = o1.v[Y]; val y2 = o2.v[Y]
            if(y1>y2) return -1
            if(y1<y2) return 1
            return -o1.v[X].compareTo(o2.v[X])
        }
    }

    val pi = Math.PI.toFloat()
    fun classifyMonotnie(edge:HalfEdge<H,V,F>, c:Comparator<Vertex<H,V,F>> = monotonyComparator) : Map<HalfEdge<H,V,F>, Monotonie> {
        val res = HashMap<HalfEdge<H,V,F>, Monotonie>()
        val h = containment()
        var key = edge().filter { it in h }.firstOrNull()
        if(key==null) return emptyMap()

        val level = h.level(key)
        val holes = h.children(key)
        val alledges = edge() + holes.flatMap { it() }.sortBy(object: Comparator<HalfEdge<H, V, F>> {
            override fun compare(o1: HalfEdge<H, V, F>?, o2: HalfEdge<H, V, F>?): Int {
                return c.compare(o1?.origin, o2?.origin)
            }
        })

        fun classify(v:HalfEdge<H,V,F>) : Monotonie {
            val vm1 = v.previous
            val vp1 = v.next
            val angle = v.innerAngle
            return if (c.compare(v.origin, vp1.origin) < 0 && c.compare(v.origin, vm1.origin) < 0) {
                if (angle < pi) Monotonie.START
                else if (angle > pi) Monotonie.SPLIT
                else Monotonie.NORM
            } else
                if (c.compare(v.origin, vp1.origin) > 0 && c.compare(v.origin, vm1.origin) > 0) {
                    if (angle < pi) Monotonie.END
                    else if (angle > pi) Monotonie.MERGE
                    else Monotonie.NORM
                } else Monotonie.NORM
        }

        fun classifyEdge(e:HalfEdge<H,V,F>) : Monotonie {
            val m = classify(e)
            if(m== Monotonie.NORM) return m
            if(e in edge) return m
            return when(m) {
                Monotonie.END -> Monotonie.MERGE
                Monotonie.START -> Monotonie.SPLIT
                Monotonie.SPLIT -> Monotonie.START
                Monotonie.MERGE -> Monotonie.END
                else -> Monotonie.NORM
            }
        }

        alledges.forEach {
            res[it] = classifyEdge(it)
        }

        return res
    }




    class Containment<H,V,F>(val containers: Map<HalfEdge<H,V,F>, Set<HalfEdge<H,V,F>>>,
                             val contained: Map<HalfEdge<H,V,F>, Set<HalfEdge<H,V,F>>>)

}