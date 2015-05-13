package ch.passenger.kotlin.graphics.mesh

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.geometry.Triangle
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.trees.Octree
import ch.passenger.kotlin.graphics.util.logging.d
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by svd on 05/05/2015.
 */

trait Vertex<H,V,F> {
    val log : Logger get() = LoggerFactory.getLogger(javaClass<Vertex<H,V,F>>())
    val id : Long
    val v: VectorF
    val data: V
    var leaving:HalfEdge<H,V,F>
    val mesh : Mesh<H,V,F>
    /**
     * @return an iterable with every edge that has this vertex as origin
     */
    fun invoke() : Iterable<HalfEdge<H,V,F>> {
        val res = arrayListOf<HalfEdge<H,V,F>>()
        if(leaving==mesh.NOEDGE) return emptyList()
        var e = leaving
        do {
            res.add(e)
            e = e.twin.next

        } while(e!=leaving && e!=mesh.NOEDGE)
        return res
    }

    val isolated : Boolean get() = leaving==mesh.NOEDGE

    val NOVERTEX : Vertex<H,V,F>

    override fun equals(other: Any?): Boolean = if(other is Vertex<*, *, *>) other.mesh==mesh && other.id==this.id else false

    override fun hashCode(): Int = v.hashCode()

    override fun toString(): String = "V$id($v)"

    fun compare(f1:Float, f2:Float) : Int = comaprator.compare(f1, f2)
    fun equals(f1:Float, f2:Float) : Boolean = comaprator.compare(f1, f2)==0

    val comaprator:Comparator<Float> get() = object : Comparator<Float> {
        fun ulps(a:Float, b:Float) : Int = Math.abs(java.lang.Float.floatToIntBits(b)-java.lang.Float.floatToIntBits(a))
        val epsilon : Float get() {
            val min = mesh.extent.min().min()!!
            val max = mesh.extent.max().max()!!
            val ulps = ulps(min, max)
            val ae = Math.abs(max-min)/ulps
            return if(ae==Float.NEGATIVE_INFINITY || ae==Float.POSITIVE_INFINITY) java.lang.Float.intBitsToFloat(
                    java.lang.Float.floatToIntBits(Float.MIN_VALUE)-1
            ) else ae*100
        }
        override fun compare(o1: Float?, o2: Float?): Int {
            if(o1 == null) return -1
            if(o2==null) return 1
            if(o1.isNaN()) return -1
            if(o2.isNaN())  return 1
            log.d {"$o1 $o2 d: ${Math.abs(o1-o2)} epsilon: $epsilon"}
            if(Math.abs(o1-o2)<=epsilon) return 0
            return o1.compareTo(o2)
        }
    }
}
trait HalfEdge<H,V,F> {
    val origin : Vertex<H,V,F>
    val twin:HalfEdge<H,V,F>
    val destination : Vertex<H,V,F> get() = twin.origin
    val key:Pair<Vertex<H,V,F>, Vertex<H,V,F>> get() = origin to twin.origin
    val data: H
    var next:HalfEdge<H,V,F>
    val previous:HalfEdge<H,V,F> get() {
        val v = origin().map{it.twin}.firstOrNull{it.next==this}
        if(v==null) return NOEDGE
        else return v
    }
    val cycle : Boolean get() = this().any{it.next==this}
    val rim : Boolean get() = cycle && twin().all {it.twin in this} && this().all {it.twin in twin}
    var left:Face<H,V,F>

    val innerAngleRaw : Float get() {
            if(this==NOEDGE || previous==NOEDGE) return Float.NaN
            val a = origin.v
            val c = twin.origin.v
            val b = previous.origin.v
            val v1 = (b-a); val v2 = (c-a)
            var ang = Math.atan2(v2.y.toDouble(),v2.x.toDouble()) - Math.atan2(v1.y.toDouble(),v1.x.toDouble())
            return ang.toFloat()
    }

    val innerAngle : Float get() {
        var ang = innerAngleRaw
        if(ang<0) ang += (2*Math.PI).toFloat()
        return ang.toFloat()
    }

    val sumInnerAngles : Float get() = this().map { it.innerAngle }.sum()
    val piLaw : Float get() = if(!cycle) Float.NaN else ((this().count()-2)*Math.PI).toFloat()
    val insideLooking : Boolean get() = origin.equals(sumInnerAngles,piLaw)
    val dir : VectorF get() = destination.v-origin.v
    val cross : VectorF get() = if(previous==NOEDGE) VectorF(origin.v.dimension) {Float.NaN} else previous.dir.cross(dir)
    val sumCross : VectorF get() = if(!cycle) VectorF(origin.v.dimension) {Float.NaN} else
        this().map { it.cross }.fold<VectorF,VectorF>(VectorF(origin.v.dimension) {0f}) {acc, it -> acc+it}

    fun contains(e:HalfEdge<H,V,F>) : Boolean = this().any { it==e }

    override fun equals(other: Any?): Boolean = if(other is HalfEdge<*,*,*>) other.key==key else false

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "${origin.id}->${destination.id}"

    val NOEDGE : HalfEdge<H,V,F>
    fun invoke() : Iterable<HalfEdge<H,V,F>> = object : Iterable<HalfEdge<H,V,F>> {
        override fun iterator(): Iterator<HalfEdge<H, V, F>> = object: Iterator<HalfEdge<H, V, F>> {
            var current : HalfEdge<H,V,F>? = null
            override fun hasNext(): Boolean = current!=NOEDGE && current!=this@HalfEdge

            override fun next(): HalfEdge<H, V, F> {
                if(current==null) current=this@HalfEdge
                var r = current!!
                current = current!!.next
                return r
            }
        }
    }
    val line:LineSegment get() = LineSegment.create(origin.v, destination.v)
}

trait Face<H,V,F> {
    open var name : String
    open val parent : Face<H,V,F>
    val id : Int
    val edge:HalfEdge<H,V,F>
    val data:F
    var hole:Boolean
    val infinity:Boolean get() = false
    val properFace: Boolean get() = this!=NOFACE && !hole && !infinity

    /**
     * return a list of tringales fully contained in this face
     */
    val triangles : Iterable<Triangle> get() {
        if(edge().count()==3) {
            return listOf(Triangle(edge.origin.v, edge.next.origin.v, edge.next.next.origin.v))
        } else {
            return edge().map { if(it.innerAngle<Math.PI) Triangle(it.previous.origin.v, it.origin.v, it.next.origin.v) else null}.filterNotNull()
        }
    }

    override fun equals(other: Any?): Boolean = if(other is Face<*,*,*> && other.properFace) edge().all { it in other.edge() } && other.edge().all { it in edge() } else false

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "F$name($edge)"
    open val NOFACE : Face<H,V,F>
}

class Mesh<H,V,F>(extent:AlignedCube, val faceFactory:(e:HalfEdge<H,V,F>,parent:Face<H,V,F>)->F) {
    private var vids : Long = 0L

    val NOVERTEX = object : Vertex<H,V,F> {
        override val id: Long
            get() = -1L
        override val v: VectorF
            get() = MutableVectorF(0, 0, 0)
        override val data: V
            get() = throw UnsupportedOperationException()
        override var leaving: HalfEdge<H, V, F>
            get() = throw UnsupportedOperationException()
        set(v) {}
        override val NOVERTEX: Vertex<H, V, F>
            get() = this
        override val mesh: Mesh<H, V, F>
            get() = this@Mesh
        override fun invoke(): Iterable<HalfEdge<H, V, F>> = emptyList()
        override fun equals(other: Any?): Boolean = identityEquals(other)
        override fun toString(): String = "NOVERTEX"
    }
    val NOEDGE = object : HalfEdge<H,V,F> {
        override val key: Pair<Vertex<H, V, F>, Vertex<H, V, F>>
            get() = NOVERTEX to NOVERTEX
        override val data: H
            get() = throw UnsupportedOperationException()
        override val twin: HalfEdge<H, V, F>
            get() = this
        override var next: HalfEdge<H, V, F>
            get() = this
        set(v){}
        override val previous: HalfEdge<H, V, F>
            get() = this
        override var left: Face<H, V, F>
            get() = NOFACE
        set(v) {}
        override val origin: Vertex<H, V, F>
            get() = NOVERTEX
        override val NOEDGE: HalfEdge<H, V, F>
            get() = this

        override fun invoke(): Iterable<HalfEdge<H, V, F>> = emptyList()
        override fun equals(other: Any?): Boolean = identityEquals(other)
        override fun toString(): String = "NOEDGE"
    }
    val NOFACE = object : Face<H,V,F> {
        override val parent: Face<H, V, F>
            get() = throw UnsupportedOperationException()
        override var name: String = "NOFACE"
        override val id: Int get() = -1
        override val edge: HalfEdge<H, V, F> get() = throw UnsupportedOperationException()
        override val data: F get() = throw UnsupportedOperationException()
        override val NOFACE: Face<H, V, F> get() = this
        override var hole: Boolean get() = false
            set(v) {}
        override val infinity: Boolean get() = false
        override val properFace: Boolean get() = false
        override val triangles: Iterable<Triangle> get() = emptyList()

        override fun equals(other: Any?): Boolean = identityEquals(other)
        override fun toString(): String = "NOFACE"
    }

    val INFINITY = object : Face<H,V,F> {
        override val parent: Face<H, V, F> get() = NOFACE
        override var name: String = "INF"
        override val id: Int get() = -3
        override val edge: HalfEdge<H, V, F> get() = NOEDGE
        override val data: F get() = throw UnsupportedOperationException()
        override val infinity: Boolean get() = true
        override var hole: Boolean get() = false
            set(v) {}
        override val NOFACE: Face<H, V, F> get() = this@Mesh.NOFACE
        override val triangles: Iterable<Triangle> get() = emptyList()
        override fun equals(other: Any?): Boolean = identityEquals(other)
    }



    private inner class MVertex(override val data:V, override val v: VectorF) : Vertex<H,V,F> {
        override val id: Long = vids++
        override var leaving: HalfEdge<H, V, F> = NOEDGE
        override val NOVERTEX: Vertex<H, V, F>
            get() = this@Mesh.NOVERTEX
        override val mesh: Mesh<H, V, F> get() = this@Mesh
    }

    private inner class MEdge(override val origin: Vertex<H, V, F>, override val data: H) : HalfEdge<H,V,F> {
        var _twin : HalfEdge<H, V, F>? = null
        override val twin: HalfEdge<H, V, F> get() = _twin!!

        override var next: HalfEdge<H, V, F> = NOEDGE
        override var left: Face<H, V, F> = NOFACE

        override val NOEDGE: HalfEdge<H, V, F> get() = this@Mesh.NOEDGE
    }

    private inner class MFace(override val parent: Face<H, V, F>, override val edge:HalfEdge<H, V, F>, override val data:F, name:String, override val id:Int=fids++) : Face<H,V,F> {
        override var name: String = name
            get() = if($name.isEmpty()) "$id" else $name
            set(v) {$name = v}
        override val NOFACE: Face<H, V, F> get() = this@Mesh.NOFACE
        override var hole: Boolean=if(parent!=NOFACE) parent.hole else false

        init {
            edge().forEach { it.left=this }
            octree + this
            mfaces[id] = this
            handleFaceAdd(this)
        }
    }

    public fun get(v:VectorF) : Vertex<H,V,F>? = octree.vat(v)
    public fun get(id:Long) : Vertex<H,V,F>? = mvertices[id]
    public fun get(id:Int) : Face<H,V,F>? = mfaces[id]
    public fun get(v0:Vertex<H,V,F>,v1:Vertex<H,V,F>) : HalfEdge<H,V,F>? = medges[v0 to v1]
    public fun contains(v:VectorF) : Boolean = octree.vat(v) != null

    private var fids : Int = 0
    private val mvertices:MutableMap<Long,Vertex<H, V, F>> = HashMap()
    private val medges:MutableMap<Pair<Vertex<H, V, F>,Vertex<H, V, F>>,HalfEdge<H, V, F>> = hashMapOf()
    private val mfaces:MutableMap<Int,Face<H, V, F>> = hashMapOf()
    private final val octree : Octree<H,V,F> = Octree(extent, 16)
    val extent:AlignedCube get() = octree.extent

    private val vahandlers : MutableSet<(WeakReference<(Vertex<H, V, F>)->Unit>)> = hashSetOf()
    private val vrhandlers : MutableSet<(WeakReference<(Vertex<H, V, F>)->Unit>)> = hashSetOf()
    private val eahandlers : MutableSet<(WeakReference<(HalfEdge<H, V, F>)->Unit>)> = hashSetOf()
    private val erhandlers : MutableSet<(WeakReference<(HalfEdge<H, V, F>)->Unit>)> = hashSetOf()
    private val fahandlers : MutableSet<(WeakReference<(Face<H, V, F>)->Unit>)> = hashSetOf()
    private val frhandlers : MutableSet<(WeakReference<(Face<H, V, F>)->Unit>)> = hashSetOf()

    fun addVertexHandler(add:(Vertex<H,V,F>)->Unit={}, remove:(Vertex<H,V,F>)->Unit={}) {
        vahandlers add WeakReference(add)
        vrhandlers add WeakReference(remove)
    }

    fun handleVertexAdd(v:Vertex<H,V,F>) {
        vahandlers.forEach { if(it.get()!=null) it.get()(v) else vahandlers.remove(it) }
    }

    fun handleVertexRemove(v:Vertex<H,V,F>) {
        vrhandlers.forEach { if(it.get()!=null) it.get()(v) else vrhandlers.remove(it) }
    }

    fun addEdgeHandler(add:(HalfEdge<H,V,F>)->Unit={}, remove:(HalfEdge<H,V,F>)->Unit={}) {
        eahandlers add WeakReference(add)
        erhandlers add WeakReference(remove)
    }

    fun handleEdgeAdd(v:HalfEdge<H,V,F>) {
        eahandlers.forEach { if(it.get()!=null) it.get()(v) else eahandlers.remove(it) }
    }

    fun handleEdgeRemove(v:HalfEdge<H,V,F>) {
        erhandlers.forEach { if(it.get()!=null) it.get()(v) else erhandlers.remove(it) }
    }

    fun addFaceHandler(add:(Face<H,V,F>)->Unit={}, remove:(Face<H,V,F>)->Unit={}) {
        fahandlers add WeakReference(add)
        frhandlers add WeakReference(remove)
    }

    fun handleFaceAdd(v:Face<H,V,F>) {
        fahandlers.forEach { if(it.get()!=null) it.get()(v) else fahandlers.remove(it) }
    }

    fun handleFaceRemove(v:Face<H,V,F>) {
        frhandlers.forEach { if(it.get()!=null) it.get()(v) else frhandlers.remove(it) }
    }

    fun add(vec:VectorF, data:V) : Vertex<H,V,F> {
        val vd = octree.vat(vec)
        if(vd !=null) throw DuplicateElementException(vd, "Duplicate Vertex $vd")
        val v = MVertex(data, vec)
        mvertices[v.id] = v
        octree + v
        handleVertexAdd(v)
        return v
    }

    fun minus(v:Vertex<H,V,F>)  {
        if(!v.isolated) throw IllegalStateException("$v still points to ${v.leaving}")
        mvertices.remove(v.id)
        octree - v
        vrhandlers.forEach { it.get()(v) }
    }

    fun plus(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>, data:H, twin:H=data) : Mesh<H,V,F> {
        if(v0==v1) throw IllegalArgumentException("zero length edge $v0=>$v1 not allowed")
        val key = v0 to v1
        if(key in medges) throw DuplicateElementException(medges[key]!!, "Duplicate Edge ${medges[key]}")
        createEdge(v0, v1, data, twin)

        return this
    }

    private fun createEdge(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>, data:H, datatwin:H) : HalfEdge<H,V,F> {
        val e = MEdge(v0, data)
        val twin = MEdge(v1, datatwin)
        e._twin = twin
        twin._twin = e
        medges[e.key] = e
        medges[twin.key] = twin
        octree + e
        octree + twin

        if(v0.leaving==NOEDGE) v0.leaving=e
        if(v1.leaving==NOEDGE) v1.leaving=twin

        landingAt(e);
        assert(e.previous==NOEDGE||e.previous.next==e, "bad link in main edge $e")
        assert(e.twin.previous==NOEDGE||e.twin.previous.next==e.twin, "bad link in twin edge ${e.twin}")
        landingAt(e.twin)
        assert(e.previous==NOEDGE||e.previous.next==e, "bad link in main edge $e")
        assert(e.twin.previous==NOEDGE||e.twin.previous.next==e.twin, "bad link in twin edge ${e.twin}")

        handleEdgeAdd(e); handleEdgeAdd(e.twin)
        return e
    }

    fun unlinkFace(f:Face<H,V,F>) {
        f.edge().forEach { it.left=NOFACE }
        mfaces.remove(f.id)
        octree - f
        handleFaceRemove(f)
    }

    fun linkFace(e:HalfEdge<H,V,F>, p:Face<H,V,F>, n:String="") {
        val f = MFace(p, e, faceFactory(e, p), n)
        mfaces[f.id] = f
    }

    fun landingAt(e:HalfEdge<H,V,F>) {
        if(e.next!=NOEDGE) {
            if(!e.left.properFace && e.cycle && e.insideLooking) {
                linkFace(e, NOFACE)
            }
            return
        }
        val next = e.destination().filter { it!=e.twin && it!=NOEDGE }.sortBy {VectorF.angle3p(e.origin.v, e.destination.v, it.destination.v)}.firstOrNull()
        if(next!=null) {
            if(next.cycle) {
                return split(e, next)
            }
            if(next.left.properFace) throw IllegalStateException()
            val np = next.previous
            e.next = next
            if(np!=NOEDGE) np.next=e.twin
        }
        val prev = e.origin().filter { assert(it.origin==e.origin); it!=e }.map { it.twin }.sortBy {VectorF.angle3p(it.origin.v, e.origin.v, e.destination.v)}.firstOrNull()
        if(prev!=null && prev!=NOEDGE && e.previous==NOEDGE) {
            assert(prev!=e.twin)
            val pn = prev.next
            if(pn!=NOEDGE) e.twin.next=pn
            prev.next = e
        }
        if(e.cycle && e.insideLooking && !e.left.properFace) {
            linkFace(e, NOFACE)
        }
        if(e.twin.cycle && e.twin.insideLooking && !e.twin.left.properFace) {
            linkFace(e.twin, NOFACE)
        }
    }

    fun split(e:HalfEdge<H,V,F>, sp:HalfEdge<H,V,F>) {
        val prev = sp().first { it.destination==e.origin }
        val twnext = prev.next
        val twprev = sp.previous
        val parent = if(sp.left.properFace) sp.left else NOFACE
        prev.next = e
        e.next = sp
        e.twin.next = twnext
        twprev.next = e.twin
        if(parent.properFace) unlinkFace(parent)
        linkFace(e, parent, "SPLIT(${if(parent.properFace)parent.name else ""}, ${e.origin.id}->${e.destination.id})")
        linkFace(e.twin, parent, "SPLIT(${if(parent.properFace)parent.name else ""}, ${e.twin.origin.id}->${e.twin.destination.id})")
    }

    val edges:Iterable<HalfEdge<H,V,F>> = medges.values()
    val vertices:Iterable<Vertex<H,V,F>> = mvertices.values()
    val faces:Iterable<Face<H,V,F>> = mfaces.values()
    fun find(hotzone:AlignedCube) : Octree.Result<H,V,F> = octree.find(hotzone)
    fun findEdges(hotzone:AlignedCube) : Iterable<HalfEdge<H,V,F>> = octree.findEdges(hotzone)
    fun findVertices(hotzone:AlignedCube) : Iterable<Vertex<H,V,F>> = octree.findVertices(hotzone)
}

open class MeshException(msg:String="", cause:Throwable?=null, suppress:Boolean=false, writable:Boolean=false) : Exception(msg, cause, suppress, writable)
class DuplicateElementException(val element:Any, msg:String="", cause:Throwable?=null, suppress:Boolean=false, writable:Boolean=false) : MeshException(msg, cause, suppress, writable)

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
            log.d{"isLeft: $v of $e"}
            return left.toInt()
        }
        /*
        if (V[i].y <= P.y) {          // start y <= P.y
            if (V[i+1].y  > P.y)      // an upward crossing
                 if (isLeft( V[i], V[i+1], P) > 0)  // P left of  edge
                     ++wn;            // have  a valid up intersect
        }
        else {                        // start y > P.y (no test needed)
            if (V[i+1].y  <= P.y)     // a downward crossing
                 if (isLeft( V[i], V[i+1], P) < 0)  // P right of  edge
                     --wn;            // have  a valid down intersect
        }
         */
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
        log.d {"wn $v -> $e"}
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

    fun containment() : Map<HalfEdge<H,V,F>, Set<HalfEdge<H,V,F>>> {
        val res : MutableMap<HalfEdge<H,V,F>, Set<HalfEdge<H,V,F>>> = hashMapOf()
        val insides = cycles().filter { it.insideLooking }
        insides.forEach {
            candidate ->
            insides.filter { it!=candidate }.forEach {
                container ->
                if(candidate().map { it.origin }.all { wn(it, container) != 0 }) {
                    if(container !in res) res[container] = hashSetOf(candidate)
                    else (res[container]!! as MutableSet) add candidate
                }
            }
        }

        return res
    }

}