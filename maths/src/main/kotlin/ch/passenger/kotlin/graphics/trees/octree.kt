package ch.passenger.kotlin.graphics.trees

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.math.MutableVectorF
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
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    val rw = ReentrantReadWriteLock()
    var root : Node = Leaf(extent)

    fun insert(v: Vertex<H, V, F>) : Octree<H,V,F> {
        if(v.v !in extent) {
            log.w {
                "$v exceeds currenr extent: $extent rebuilding tree"
            }
            extend(extent+v.v)
        }
        root = root + v
        return this
    }

    fun extend(ne:AlignedCube) {
        val nr = Leaf(ne)
        root.vertices.forEach { nr+it }
        root.edges.forEach { nr+it }
        root.faces.forEach { nr+it }
        root = nr
        log.d{"extent now $extent"}
    }

    fun insert(e: HalfEdge<H, V, F>) : Octree<H,V,F> {
        root = root + e
        return this
    }

    fun insert(f: Face<H, V, F>) : Octree<H,V,F> {
        root = root + f
        return this
    }

    fun minus(v: Vertex<H, V, F>) : Octree<H,V,F> = write {
        if(v in pendingVertices) pendingVertices.remove(v)
        root = root - v
        this
    }

    fun minus(e: HalfEdge<H, V, F>) : Octree<H,V,F> = write {
        if(e in pendingEdges) pendingEdges.remove(e)
        root = root - e
        this
    }

    fun minus(f: Face<H, V, F>) : Octree<H,V,F> = write {
        if(f in pendingFaces) pendingFaces.remove(f)
        root = root - f
        this
    }

    fun plus(v: Vertex<H, V, F>) : Octree<H,V,F> = write {
        pendingVertices.offer(v)
        this
    }

    fun plus(e: HalfEdge<H, V, F>) : Octree<H,V,F> = write {
        pendingEdges.offer(e)
        this
    }

    fun plus(f: Face<H, V, F>) : Octree<H,V,F> = write {
        pendingFaces.offer(f)
        this
    }

    val extent : AlignedCube get() = query { root.extent }
    val pendingFaces : BlockingQueue<Face<H,V,F>>
    val pendingEdges : BlockingQueue<HalfEdge<H,V,F>>
    val pendingVertices : BlockingQueue<Vertex<H,V,F>>

    init {
        pendingFaces= LinkedBlockingQueue()
        pendingEdges = LinkedBlockingQueue()
        pendingVertices = LinkedBlockingQueue()

        Executors.newScheduledThreadPool(1).scheduleAtFixedRate({update()}, 500L, 500L, TimeUnit.MILLISECONDS)
    }

    private fun update() {
        rw.writeLock().lock()
        try {
            while (pendingVertices.size()>0 || pendingEdges.size()>0 || pendingFaces.size()>0) {
                val verts = arrayListOf<Vertex<H,V,F>>()
                val edges: ArrayList<HalfEdge<H, V, F>>
                pendingVertices.drainTo(verts)
                if(verts.size()>0) {
                    val min = MutableVectorF(verts.first().v)
                    val max = MutableVectorF(min)
                    verts.forEach {
                        min().forEachIndexed { i, fl -> if(it.v[i]<fl) min[i] =  it.v[i]}
                        max().forEachIndexed { i, fl -> if(it.v[i]>fl) max[i] =  it.v[i]}
                    }
                    if(min !in extent || max !in extent) {
                        extend(AlignedCube(min, max)+extent)
                    }
                    log.debug("inserting ${verts.size()} vertices")
                    verts.forEach {insert(it)}
                }
                edges = arrayListOf<HalfEdge<H,V,F>>()
                pendingEdges.drainTo(edges)
                edges.forEach{insert(it)}
                val faces = arrayListOf<Face<H,V,F>>()
                pendingFaces.drainTo(faces)
                faces.forEach{insert(it)}
            }

        } finally {
            rw.writeLock().unlock()
        }
    }

    private val ready : Boolean get() = pendingEdges.size()==0 && pendingFaces.size()==0 && pendingVertices.size()==0

    fun<T> query(cb:()->T) : T {
        rw.readLock().lock()
        try {
            while(!ready) {
                rw.readLock().unlock()
                update()
                rw.readLock().lock()
            }
            return cb()
        }finally {
            rw.readLock().unlock()
        }
    }

    fun<T> write(cb:()->T) : T {
        rw.writeLock().lock()
        try {
            if(!ready) update()
            return cb()
        }finally {
            rw.writeLock().unlock()
        }
    }

    fun vat(v:VectorF) : Vertex<H,V,F>? = query { root.vat(v) }

    fun find(hotzone:AlignedCube) : Result<H,V,F>  = query { root.find(hotzone) }
    fun findEdges(hotzone:AlignedCube) : Iterable<HalfEdge<H,V,F>> = query { root.findEdges(hotzone) }
    fun findVertices(hotzone:AlignedCube) : Iterable<Vertex<H,V,F>> = query { root.findVertices(hotzone) }
    fun findFaces(hotzone:AlignedCube) : Iterable<Face<H,V,F>> = query { root.findFaces(hotzone) }
}

