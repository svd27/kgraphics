package ch.passenger.kotlin.graphics.trees

import ch.passenger.kotlin.graphics.geometry.AlignedCube
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import ch.passenger.kotlin.graphics.mesh.Vertex

/**
 * Created by svd on 05/05/2015.
 */
class Octree<H,V,F>(val extent:AlignedCube, val cellLoad:Int, val collapseLoad:Int=0) {
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
                return branch
            }
            return this
        }

        override fun vat(v: VectorF): Vertex<H, V, F>? = vertices.firstOrNull { it.v == v }
    }

    inner class Branch(extent:AlignedCube) : Node(extent) {
        val children : Array<Node>

        init {
            val split = extent.split()
            children = Array(split.count()) {
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
    }

    var root : Node = Leaf(extent)

    fun plus(v: Vertex<H, V, F>) : Octree<H,V,F> {
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

    fun vat(v:VectorF) : Vertex<H,V,F>? = root.vat(v)
}