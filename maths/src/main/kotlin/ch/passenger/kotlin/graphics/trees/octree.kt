package ch.passenger.kotlin.graphics.trees

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Vertex
import ch.passenger.kotlin.graphics.util.logging.d
import ch.passenger.kotlin.graphics.util.logging.w
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.reflect.jvm.java

/**
 * Created by svd on 05/05/2015.
 */
class Octree<H,V,F>(extent:AlignedCube, val cellLoad:Int, val collapseLoad:Int=0) {
    val log = LoggerFactory.getLogger(Octree::class.java)
    inner abstract class Node(val extent:AlignedCube) {
        abstract fun plus(v: Vertex<H, V, F>) : Node
        abstract fun minus(v: Vertex<H, V, F>) : Node
        abstract fun plus(e: HalfEdge<H, V, F>) : Node
        abstract fun minus(e: HalfEdge<H, V, F>) : Node
        abstract fun plus(f: Face<H, V, F>) : Node
        abstract fun minus(f: Face<H, V, F>) : Node
        abstract val nvertices : Int
        abstract val nedges : Int
        abstract val nfaces : Int
        abstract val vertices : Iterable<Vertex<H,V,F>>
        abstract val edges : Iterable<HalfEdge<H,V,F>>
        abstract val faces : Iterable<Face<H,V,F>>
        abstract fun vat(v:VectorF) : Vertex<H,V,F>?
        abstract fun find(hotzone:AlignedCube) : Result<H,V,F>
        abstract fun findEdges(hotzone:AlignedCube) : Iterable<HalfEdge<H,V,F>>
        abstract fun findVertices(hotzone:AlignedCube) : Iterable<Vertex<H,V,F>>
        abstract fun findFaces(hotzone:AlignedCube) : Iterable<Face<H,V,F>>
    }

    inner class Leaf(extent:AlignedCube) : Node(extent) {
        override val vertices:MutableSet<Vertex<H,V,F>> = hashSetOf()
        override val edges:MutableSet<HalfEdge<H,V,F>> = hashSetOf()
        override val faces:MutableSet<Face<H, V, F>> = hashSetOf()
        override val nvertices: Int get() =vertices.size()
        override val nedges: Int get() = edges.size()
        override val nfaces: Int get() = faces.size()

        override fun plus(v: Vertex<H, V, F>): Node {
            vertices add v
            return extend()
        }

        override fun minus(v: Vertex<H, V, F>): Node {
            vertices remove v
            return this
        }

        override fun plus(e: HalfEdge<H, V, F>): Node {
            edges add e
            return extend()
        }

        override fun minus(e: HalfEdge<H, V, F>): Node {
            edges remove e
            return this
        }

        override fun plus(f: Face<H, V, F>): Node {
            faces add f
            return extend()
        }

        override fun minus(f: Face<H, V, F>): Node {
            faces remove f
            return this
        }

        fun extend() : Node {
            if(nedges+nvertices+nfaces>cellLoad) {
                val branch = Branch(extent)
                vertices.forEach { branch + it }
                edges.forEach { branch + it }
                faces.forEach { branch + it }
                log.trace("created branch ${branch.extent} w/ ${branch.children.size()} subs")
                branch.children.forEachIndexed {
                    idx, c ->
                    log.trace("$idx: ${c.extent}: ${c.edges.count()} edges ${c.vertices.count()} vertices")
                }
                return branch
            }
            return this
        }

        override fun vat(v: VectorF): Vertex<H, V, F>? = vertices.firstOrNull { it.v == v }

        override fun find(hotzone: AlignedCube): Result<H, V, F> = Result(findEdges(hotzone), findVertices(hotzone), findFaces(hotzone))
        override fun findEdges(hotzone: AlignedCube): Iterable<HalfEdge<H, V, F>> = edges.filter { it in hotzone }
        override fun findVertices(hotzone: AlignedCube): Iterable<Vertex<H, V, F>> = vertices.filter { it.v in hotzone }
        override fun findFaces(hotzone: AlignedCube): Iterable<Face<H, V, F>> = faces.filter { it in hotzone }
    }

    inner class Branch(extent:AlignedCube) : Node(extent) {
        val children : Array<Node>

        init {
            val split = extent.split()
            children = Array(split.count()) {
                log.trace("branch extend: $it: ${split.elementAt(it)}")
                Leaf(split.elementAt(it))
            }
        }

        override val nvertices: Int get() = children.sumBy { it.nvertices }
        override val nedges: Int get() = children.sumBy { it.nedges }
        override val nfaces: Int get() = children.sumBy { it.nfaces }

        override val vertices: Iterable<Vertex<H, V, F>> get() = children.flatMap { it.vertices }.distinct()
        override val edges: Iterable<HalfEdge<H, V, F>> get() = children.flatMap { it.edges }.distinct()
        override val faces: Iterable<Face<H, V, F>> get() = children.flatMap { it.faces }.distinct()

        override fun plus(v: Vertex<H, V, F>): Node {
            children.filter { v.v in it.extent }.forEach { it+v }
            return this
        }

        override fun plus(e: HalfEdge<H, V, F>): Node {
            children.filter { e in it.extent }.forEach { it+e }
            return this
        }

        override fun plus(f: Face<H, V, F>): Node {
            children.filter { f in it.extent }.forEach { it+f }
            return this
        }

        fun collapse() : Node {
            if(nedges+nvertices+nfaces<=collapseLoad) {
                val node = Leaf(extent)
                children.forEach {
                    it.vertices.forEach { node+it }
                    it.edges.forEach { node+it }
                    it.faces.forEach { node+it }
                }
                return node
            }
            return this
        }

        override fun minus(v: Vertex<H, V, F>): Node {
            children.filter { v.v in it.extent }.forEach { it-v }
            return collapse()
        }

        override fun minus(e: HalfEdge<H, V, F>): Node {
            children.filter { e in it.extent }.forEach { it-e }
            return collapse()
        }

        override fun minus(f: Face<H, V, F>): Node {
            children.filter { f in it.extent }.forEach { it-f }
            return collapse()
        }

        override fun vat(v: VectorF): Vertex<H, V, F>? {
            vertices.forEach { if(it.v == v) return it }
            return null
        }

        override fun find(hotzone: AlignedCube): Result<H, V, F> = Result(findEdges(hotzone), findVertices(hotzone), findFaces(hotzone))

        override fun findEdges(hotzone: AlignedCube): Iterable<HalfEdge<H, V, F>> = children.filter { hotzone intersects  it.extent }.flatMap { it.findEdges(hotzone) }
        override fun findVertices(hotzone: AlignedCube): Iterable<Vertex<H, V, F>> = children.filter {  hotzone intersects  it.extent }.flatMap { it.findVertices(hotzone) }
        override fun findFaces(hotzone: AlignedCube): Iterable<Face<H, V, F>> = children.filter {  hotzone intersects  it.extent }.flatMap { it.findFaces(hotzone) }
    }

    data class Result<H,V,F>(val edges:Iterable<HalfEdge<H,V,F>> = emptyList(), val vertices:Iterable<Vertex<H,V,F>> = emptyList(), val faces:Iterable<Face<H,V,F>> = emptyList() )

    var root : Node = Leaf(extent)

    fun plus(v: Vertex<H, V, F>) : Octree<H,V,F> {
        if(v.v !in extent) {
            log.w {
                "$v exceeds currenr extent: $extent rebuilding tree"
            }
            val nr = Leaf(extent+v.v)
            root.vertices.forEach { nr+it }
            root.edges.forEach { nr+it }
            root.faces.forEach { nr+it }
            root = nr
            log.d{"extent now $extent"}
        }
        root = root + v
        return this
    }
    fun minus(v: Vertex<H, V, F>) : Octree<H,V,F> {
        root = root - v
        return this
    }
    fun plus(e: HalfEdge<H, V, F>) : Octree<H,V,F> {
        root = root + e
        return this
    }
    fun minus(e: HalfEdge<H, V, F>) : Octree<H,V,F> {
        root = root - e
        return this
    }
    fun plus(f: Face<H, V, F>) : Octree<H,V,F> {
        root = root + f
        return this
    }
    fun minus(f: Face<H, V, F>) : Octree<H,V,F> {
        root = root - f
        return this
    }
    val extent : AlignedCube get() = root.extent



    fun vat(v:VectorF) : Vertex<H,V,F>? = root.vat(v)

    fun find(hotzone:AlignedCube) : Result<H,V,F>  = root.find(hotzone)
    fun findEdges(hotzone:AlignedCube) : Iterable<HalfEdge<H,V,F>> = root.findEdges(hotzone)
    fun findVertices(hotzone:AlignedCube) : Iterable<Vertex<H,V,F>> = root.findVertices(hotzone)
    fun findFaces(hotzone:AlignedCube) : Iterable<Face<H,V,F>> = root.findFaces(hotzone)
}

trait DeferredBuilder<T> {
    val q : Queue<T>
    val lock : ReentrantReadWriteLock
    val exec : java.util.concurrent.ExecutorService
    val settleTs : Int get() = 5

    fun add(vararg t:T ) = with(lock.writeLock()) {q.addAll(t); if(q.size()>settleTs) settle(q)}
    fun settle(q:PriorityQueue<T>)
    fun task(vararg t:T) : ()->Unit
    fun create() {
        with(lock.writeLock()) {
            q.forEach {
                exec.submit(task(it))
            }
        }
    }

    val ready : Boolean get() {with(lock.readLock()){q.isEmpty()}}

    fun onReady(cb:()->Unit) = with(lock.readLock()) {cb()}

    fun<T> with(l: Lock, cb:()->T) : T {
        l.lock()
        try {
            return cb()
        } finally {
            l.unlock()
        }
    }
}