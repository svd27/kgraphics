package ch.passenger.kotlin.graphics.mesh

import ch.passenger.kotlin.graphics.geometry.AlignedCube
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
        var e = leaving
        while(e.origin==this) {
            res.add(e)
            e = e.twin.next
        }
        return res
    }

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
        val v = origin().firstOrNull{it.twin.next==this}
        if(v==null) return NOEDGE
        else return v
    }
    val cycle : Boolean get() = this().any{it.next==this}
    var left:Face<H,V,F>
    override fun equals(other: Any?): Boolean = if(other is HalfEdge<*,*,*>) other.key==key else false

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "HE$key($origin,$destination)"

    val NOEDGE : HalfEdge<H,V,F>
    fun invoke() : Iterable<HalfEdge<H,V,F>> = object : Iterable<HalfEdge<H,V,F>> {
        override fun iterator(): Iterator<HalfEdge<H, V, F>> = object: Iterator<HalfEdge<H, V, F>> {
            var current = this@HalfEdge
            override fun hasNext(): Boolean = current!=NOEDGE

            override fun next(): HalfEdge<H, V, F> {
                var r = current
                current = current.next
                return r
            }
        }
    }
    val line:LineSegment get() = LineSegment.create(origin.v, destination.v)
}

trait Face<H,V,F> {
    open var name : String
    val id : Int
    val edge:HalfEdge<H,V,F>
    val data:F
    val hole:Boolean get() = false
    val infinity:Boolean get() = false
    override fun equals(other: Any?): Boolean = if(other is Face<*,*,*>) edge().all { it in other.edge() } && other.edge().all { it in edge() } else false

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "F$name($edge)"
}

class Mesh<H,V,F>(val extent:AlignedCube = AlignedCube(VectorF(-1f, -1f, -1f), VectorF(1f,1f,1f))) {
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
        override var left: Face<H, V, F>
            get() = NOFACE
        set(v) {}
        override val origin: Vertex<H, V, F>
            get() = NOVERTEX
        override val NOEDGE: HalfEdge<H, V, F>
            get() = this
    }
    val NOFACE = object : Face<H,V,F> {
        override val name: String = "NOFACE"
        override val id: Int get() = -1
        override val edge: HalfEdge<H, V, F>
            get() = throw UnsupportedOperationException()
        override val data: F
            get() = throw UnsupportedOperationException()

    }

    val HOLE = object : Face<H,V,F> {
        override val name: String = "HOLE"
        override val id: Int get() = -2
        override val edge: HalfEdge<H, V, F>
            get() = NOEDGE
        override val data: F
            get() = throw UnsupportedOperationException()
        override val hole: Boolean
            get() = true
    }

    val INFINITY = object : Face<H,V,F> {
        override val name: String = "INF"
        override val id: Int get() = -3
        override val edge: HalfEdge<H, V, F>
            get() = NOEDGE
        override val data: F
            get() = throw UnsupportedOperationException()
        override val infinity: Boolean
            get() = true
    }



    private inner class MVertex(override val data:V, override val v: VectorF) : Vertex<H,V,F> {
        override val id: Long = vids++
        override var leaving: HalfEdge<H, V, F> = NOEDGE
        override val NOVERTEX: Vertex<H, V, F>
            get() = NOVERTEX
        override val mesh: Mesh<H, V, F> get() = this@Mesh
    }

    private inner class MEdge(override val origin: Vertex<H, V, F>, override val data: H) : HalfEdge<H,V,F> {
        var _twin : HalfEdge<H, V, F>? = null
        override val twin: HalfEdge<H, V, F> get() = _twin!!

        override var next: HalfEdge<H, V, F> = NOEDGE

        override var left: Face<H, V, F> = NOFACE


        override val NOEDGE: HalfEdge<H, V, F>
            get() = NOEDGE
    }

    private inner class MFace(override val edge:HalfEdge<H, V, F>, override val data:F, name:String, override val id:Int=fids++) : Face<H,V,F> {
        override var name: String = name
            get() = if($name.isEmpty()) "$id" else $name
            set(v) {$name = v}
    }

    public fun get(v:VectorF) : Vertex<H,V,F>? = octree.vat(v)
    public fun get(id:Long) : Vertex<H,V,F>? = vertices[id]
    public fun get(id:Int) : Face<H,V,F>? = faces[id]
    public fun get(v0:Vertex<H,V,F>,v1:Vertex<H,V,F>) : HalfEdge<H,V,F>? = edges[v0 to v1]
    public fun contains(v:VectorF) : Boolean = octree.vat(v) != null

    private var fids : Int = 0
    private val vertices:MutableMap<Long,Vertex<H, V, F>> = HashMap()
    private val edges:MutableMap<Pair<Vertex<H, V, F>,Vertex<H, V, F>>,HalfEdge<H, V, F>> = hashMapOf()
    private val faces:MutableMap<Int,Face<H, V, F>> = hashMapOf()
    private final val octree : Octree<H,V,F> = Octree(extent)

    private val vahandlers : Set<(WeakReference<(Vertex<H, V, F>)->Unit>)->Unit> = hashSetOf()

    fun plus(vec:VectorF, data:V) : Vertex<H,V,F> {
        val vd = octree.vat(vec)
        if(vd !=null) throw DuplicateElementException(vd, "Duplicate Vertex $vd")
        val v = MVertex(data, vec)
        vertices[v.id] = v
        octree + v
        return v
    }

    fun minus(v:Vertex<H,V,F>)  {
        vertices.remove(v.id)
        octree - v
    }

    fun plus(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>, data:H, twin:H=data) : Mesh<H,V,F> {
        val key = v0 to v1
        if(key in edges) throw DuplicateElementException(edges[key]!!, "Duplicate Vertex ${edges[key]}")
        createEdge(v0, v1, data, twin)

        return this
    }

    private fun createEdge(v0:Vertex<H,V,F>, v1:Vertex<H,V,F>, data:H, datatwin:H) : HalfEdge<H,V,F> {
        val e = MEdge(v0, data)
        val twin = MEdge(v1, datatwin)
        e._twin = twin
        twin._twin = e
        edges[e.key] = e
        edges[twin.key] = twin
        octree + e + twin
        if(v0.leaving==NOEDGE) v0.leaving=e
        if(v1.leaving==NOEDGE) v1.leaving=twin
        return e
    }

}

open class MeshException(msg:String="", cause:Throwable?=null, suppress:Boolean=false, writable:Boolean=false) : Exception(msg, cause, suppress, writable)
class DuplicateElementException(val element:Any, msg:String="", cause:Throwable?=null, suppress:Boolean=false, writable:Boolean=false) : MeshException(msg, cause, suppress, writable)