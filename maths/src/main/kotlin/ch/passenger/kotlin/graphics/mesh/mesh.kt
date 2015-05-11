package ch.passenger.kotlin.graphics.mesh

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.geometry.Triangle
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.trees.Octree
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by svd on 05/05/2015.
 */

trait Vertex<H,V,F> {
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
    val insideLooking : Boolean get() = sumInnerAngles==piLaw

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
    val hole:Boolean get() = false
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

class Mesh<H,V,F>(val extent:AlignedCube = AlignedCube(VectorF(-1f, -1f, -1f), VectorF(1f,1f,1f)),
                  val faceFactory:(e:HalfEdge<H,V,F>,parent:Face<H,V,F>)->F) {
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
        override val hole: Boolean get() = false
        override val infinity: Boolean get() = false
        override val properFace: Boolean get() = false
        override val triangles: Iterable<Triangle> get() = emptyList()

        override fun equals(other: Any?): Boolean = identityEquals(other)
        override fun toString(): String = "NOFACE"
    }

    val HOLE = object : Face<H,V,F> {
        override val parent: Face<H, V, F> get() = NOFACE
        override var name: String = "HOLE"
        override val id: Int get() = -2
        override val edge: HalfEdge<H, V, F> get() = NOEDGE
        override val data: F get() = throw UnsupportedOperationException()
        override val hole: Boolean get() = true
        override val NOFACE: Face<H, V, F> get() = this@Mesh.NOFACE
        override val triangles: Iterable<Triangle> get() = emptyList()
        override fun equals(other: Any?): Boolean = identityEquals(other)
    }

    val INFINITY = object : Face<H,V,F> {
        override val parent: Face<H, V, F> get() = NOFACE
        override var name: String = "INF"
        override val id: Int get() = -3
        override val edge: HalfEdge<H, V, F> get() = NOEDGE
        override val data: F get() = throw UnsupportedOperationException()
        override val infinity: Boolean get() = true
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
        if(v0==v1) throw IllegalArgumentException("null edge $v0=>$v1 not allowed")
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