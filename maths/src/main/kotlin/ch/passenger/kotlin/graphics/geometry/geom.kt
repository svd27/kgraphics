package ch.passenger.kotlin.graphics.geometry

import ch.passenger.kotlin.graphics.math.IntersectionType
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge

/**
 * Created by svd on 05/05/2015.
 */
class AlignedCube(val min: VectorF, val max: VectorF) {
    init {
        assert(min.dimension==max.dimension&&min.dimension==3)
    }
    val center : VectorF get() = min + (max-min)*.5f
    //points in order of bottom/top:left/right:front:back
    val blf = min; val brf = MutableVectorF(max.x, min.y, min.z)
    val blb = MutableVectorF(min.x, min.y, max.z)
    val brb = MutableVectorF(max.x, min.y, max.z)
    val tlf = MutableVectorF(min.x, max.y, min.z)
    val trf = MutableVectorF(max.x, max.y, min.z)
    val tlb = MutableVectorF(min.x, max.y, max.z)
    val trb = max
    val corners : Iterable<VectorF> get() = listOf(
            blf, brf, blb, brb,
            tlf, trf, tlb,  trb
    )

    public fun contains(v:VectorF) : Boolean = v().zip(min()).all { it.first > it.second } && v().zip(max()).all { it.first < it.second }
    public fun contains(v:LineSegment) : Boolean {
        val ray = Ray(v.start, v.dir)
        val inter = intersect(ray, 0f, 1f)
        return inter!=NOPE && inter.type in setOf(IntersectionType.INTERSECT, IntersectionType.COINCIDENT) && inter.t0 in 0f..1f && inter.t1 in 0f..1f
    }
    public fun contains(e:HalfEdge<*,*,*>) : Boolean = e.line in this
    public fun contains(f: Face<*, *, *>) : Boolean = f.edge().any { it.line in this }
    public fun contains(c: AlignedCube) : Boolean = c.min in this || c.max in this

    fun plus(c:AlignedCube) : AlignedCube  = AlignedCube(VectorF(min.dimension) {Math.min(min[it], c.min[it])}, VectorF(max.dimension) {Math.max(max[it], c.max[it])})
    fun minus(c: AlignedCube): AlignedCube =
            if (c in this || this in c)
                AlignedCube(VectorF(min.dimension) { Math.max(min[it], c.min[it]) }, VectorF(max.dimension) { Math.min(max[it], c.max[it]) })
            else EMPTY

    private fun v(x:Float, y:Float, z:Float) = MutableVectorF(x, y, z)

    fun split() : Iterable<AlignedCube> = listOf(
            AlignedCube(blf, center),
            AlignedCube(v(min.x, min.y, center.z), v(center.x, center.y, max.z)),
            AlignedCube(v(center.x, min.y, min.z), v(max.x, center.y, center.z)),
            AlignedCube(v(center.x, min.y, center.z), v(max.x, center.y, max.z)),
            AlignedCube(v(min.x, center.y, min.z), v(center.x, max.y, center.z)),
            AlignedCube(v(min.x, center.y, center.z), v(center.x, max.y, max.z)),
            AlignedCube(v(center.x, center.y, min.z), v(max.x, max.y, center.z)),
            AlignedCube(center, max)
    )

    fun intersect(ray:Ray, t0:Float=0f, t1:Float=1f) : BoxIntersection {
        fun ifneg(fl:Boolean) = if(fl) max else min
        var txmin = (ifneg(ray.invneg[0]).x - ray.origin.x) * ray.inverse.x
        var txmax = (ifneg(!ray.invneg[0]).x - ray.origin.x) * ray.inverse.x
        val tymin = (ifneg(ray.invneg[1]).y - ray.origin.y) * ray.inverse.y
        val tymax = (ifneg(!ray.invneg[1]).y - ray.origin.y) * ray.inverse.y

        if ( (txmin > tymax) || (tymin > txmax) )
            return NOPE;
        if (tymin > txmin)
            txmin = tymin;
        if (tymax < txmax)
            txmax = tymax;
        val tzmin = (ifneg(ray.invneg[2]).z - ray.origin.z) * ray.inverse.z
        val tzmax = (ifneg(!ray.invneg[2]).z - ray.origin.z) * ray.inverse.z
        if ( (txmin > tzmax) || (tzmin > txmax) )
            return NOPE;
        if (tzmin > txmin)
            txmin = tzmin;
        if (tzmax < txmax)
            txmax = tzmax;
        return BoxIntersection(IntersectionType.INTERSECT, txmax, txmin)
    }

    open data class BoxIntersection(val type:IntersectionType, val t0:Float, val t1:Float)
    object NOPE : BoxIntersection(IntersectionType.NOPE, 0f, 0f)

    companion object {
        val EMPTY = AlignedCube(VectorF(0, 0, 0), VectorF(0, 0, 0))
    }
}

class Ray(val origin:VectorF, val dir:VectorF) {
    val inverse : VectorF = MutableVectorF(1/dir.x, 1/dir.y, 1/dir.z)
    val invneg : Array<Boolean> = Array(inverse.dimension) {it<0}
}