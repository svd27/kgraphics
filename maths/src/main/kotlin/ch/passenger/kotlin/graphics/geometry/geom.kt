package ch.passenger.kotlin.graphics.geometry

import ch.passenger.kotlin.graphics.math.IntersectionType
import ch.passenger.kotlin.graphics.math.LineSegment
import ch.passenger.kotlin.graphics.math.MutableVectorF
import ch.passenger.kotlin.graphics.math.VectorF
import ch.passenger.kotlin.graphics.mesh.Face
import ch.passenger.kotlin.graphics.mesh.HalfEdge
import org.slf4j.LoggerFactory

/**
 * Created by svd on 05/05/2015.
 */
class AlignedCube(val min: VectorF, val max: VectorF) {
    val log = LoggerFactory.getLogger(AlignedCube::class.java)
    init {
        assert(min.dimension==max.dimension&&min.dimension==3)
        assert(min().zip(max()).all { it.first<=it.second }) {"badly defined boundaries: $min>$max"}
    }
    val center : VectorF get() = min + (max-min)*.5f
    val dimensions : VectorF get() = max-min
    val halfedges : VectorF get() = dimensions*.5f
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

    operator public fun contains(v:VectorF) : Boolean = v().zip(min()).all { it.first >= it.second } && v().zip(max()).all { it.first <= it.second }
    operator public fun contains(v: LineSegment) : Boolean {
        val ray = Ray(v.start, v.dir)
        val inter = intersect(ray, 0f, 1f)
        return inter!=NOPE && inter.type in setOf(IntersectionType.INTERSECT, IntersectionType.COINCIDENT) && (inter.t0 in 0f..1f || inter.t1 in 0f..1f)
    }
    operator public fun contains(e: HalfEdge<*, *, *>) : Boolean = e.origin.v in this || e.destination.v in this  || e.line in this
    operator public fun contains(f: Face<*, *, *>) : Boolean = f.edge().any { it.line in this } || f.triangles.any { it in this }
    public fun intersects(c: AlignedCube) : Boolean = c.min in this || c.max in this || min in c || max in c
    public fun contains(c: AlignedCube) : Boolean = c.min in this && c.max in this
    //cf: http://fileadmin.cs.lth.se/cs/Personal/Tomas_Akenine-Moller/code/tribox3.txt
    //TODO: check if this works
    operator public fun contains(t:Triangle) : Boolean {
        val v0 = t.p0 - center
        val v1 = t.p1 - center
        val v2 = t.p2 - center

        fun AXISTEST_X01(a: Float, b: Float, fa: Float, fb: Float): Boolean {
            val p0 = a * v0.y - b * v0.z;
            val p2 = a * v2.y - b * v2.z;
            var min = 0f;
            var max = 0f;
            if (p0 < p2) {
                min = p0; max = p2;
            } else {
                min = p2; max = p0;
            }

            val rad = fa * halfedges.y + fb * halfedges.z

            return (min > rad || max < -rad)
        }

        fun AXISTEST_X2(a: Float, b: Float, fa: Float, fb: Float): Boolean {
            val p0 = a * v0.y - b * v0.z;
            val p1 = a * v1.y - b * v1.z;
            var min = 0f;
            var max = 0f
            if (p0 < p1) {
                min = p0; max = p1;
            } else {
                min = p1; max = p0;
            }

            val rad = fa * halfedges.y + fb * halfedges.z;

            return (min > rad || max < -rad)
        }

        fun AXISTEST_Y02(a: Float, b: Float, fa: Float, fb: Float): Boolean {
            val p0 = -a * v0.x + b * v0.z;
            val p2 = -a * v2.x + b * v2.z
            var min = 0f;
            var max = 0f
            if (p0 < p2) {
                min = p0; max = p2;
            } else {
                min = p2; max = p0;
            }

            val rad = fa * halfedges.x + fb * halfedges.z;

            return (min > rad || max < -rad)
        }

        fun AXISTEST_Y1(a: Float, b: Float, fa: Float, fb: Float): Boolean {
            val p0 = -a * v0.x + b * v0.z;
            val p1 = -a * v1.x + b * v1.z;
            var min = 0f;
            var max = 0f

            if (p0 < p1) {
                min = p0; max = p1;
            } else {
                min = p1; max = p0;
            }

            val rad = fa * halfedges.x + fb * halfedges.z;

            return (min > rad || max < -rad)
        }

        fun AXISTEST_Z12(a: Float, b: Float, fa: Float, fb: Float): Boolean {
            val p1 = a * v1.x - b * v1.y
            val p2 = a * v2.x - b * v2.y

            var min = 0f;
            var max = 0f
            if (p2 < p1) {
                min = p2; max = p1;
            } else {
                min = p1; max = p2;
            }

            val rad = fa * halfedges.x + fb * halfedges.y;

            return (min > rad || max < -rad)
        }

        fun AXISTEST_Z0(a: Float, b: Float, fa: Float, fb: Float): Boolean {
            val p0 = a * v0.x - b * v0.y;
            val p1 = a * v1.x - b * v1.y;

            var min = 0f;
            var max = 0f
            if (p0 < p1) {
                min = p0; max = p1;
            } else {
                min = p1; max = p0;
            }

            val rad = fa * halfedges.x + fb * halfedges.y;

            return (min > rad || max < -rad)
        }

        val e0 = v1 - v0
        val e1 = v2 - v1
        val e2 = v0 - v2
        val X = 0;
        val Y = 1;
        val Z = 2

        var fex = Math.abs(e0.x)
        var fey = Math.abs(e0.y)
        var fez = Math.abs(e0.z)
        if (!AXISTEST_X01(e0.z, e0.y, fez, fey)) return false
        if (!AXISTEST_Y02(e0.z, e0.x, fez, fex)) return false
        if (!AXISTEST_Z12(e0.y, e0.x, fey, fex)) return false
        fex = Math.abs(e1.x)
        fey = Math.abs(e1.y)
        fez = Math.abs(e1.z)

        if (!AXISTEST_X01(e1[Z], e1[Y], fez, fey)) return false
        if (!AXISTEST_Y02(e1[Z], e1[X], fez, fex)) return false
        if (!AXISTEST_Z0(e1[Y], e1[X], fey, fex)) return false

        fex = Math.abs(e2.x)
        fey = Math.abs(e2.y)
        fez = Math.abs(e2.z)

        if (!AXISTEST_X2(e2[Z], e2[Y], fez, fey)) return false
        if (!AXISTEST_Y1(e2[Z], e2[X], fez, fex)) return false
        if (!AXISTEST_Z12(e2[Y], e2[X], fey, fex)) return false


        return true
    }


    operator fun plus(v:VectorF) : AlignedCube {
        val nmin = VectorF(min.dimension) {if(min[it]>v[it]) v[it] else min[it]}
        val nmax = VectorF(max.dimension) {if(max[it]<v[it]) v[it] else max[it]}
        return AlignedCube(nmin, nmax)
    }
    operator fun plus(c:AlignedCube) : AlignedCube  = AlignedCube(VectorF(min.dimension) {Math.min(min[it], c.min[it])}, VectorF(max.dimension) {Math.max(max[it], c.max[it])})
    operator fun minus(c: AlignedCube): AlignedCube =
            if (c intersects  this || this intersects  c)
                AlignedCube(VectorF(min.dimension) { Math.max(min[it], c.min[it]) }, VectorF(max.dimension) { Math.min(max[it], c.max[it]) })
            else EMPTY

    fun scale(d:Float) = AlignedCube(min-(max-min)*(d/2f), max+(max-min)*(d/2f))

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
    val volume : VectorF= max - min
    val width = volume.x
    val height = volume.y
    val depth = volume.z

    open data class BoxIntersection(val type:IntersectionType, val t0:Float, val t1:Float)
    object NOPE : BoxIntersection(IntersectionType.NOPE, 0f, 0f)

    override fun toString(): String = "AlignedCube($min, $max)"

    companion object {
        val EMPTY = AlignedCube(VectorF(0, 0, 0), VectorF(0, 0, 0))

        fun around(c:VectorF, displace:Float) : AlignedCube =
                AlignedCube(c+-displace,c+displace)
        fun from(v1:VectorF, v2:VectorF) :AlignedCube {
            val min = VectorF(v1.dimension) {
                if(v1[it]<=v2[it]) v1[it] else v2[it]
            }
            val max = VectorF(v1.dimension) {
                if(v1[it]>=v2[it]) v1[it] else v2[it]
            }
            return AlignedCube(min, max)
        }
        fun ensureVolume(c:AlignedCube,  minv:Float=1e-9f) : AlignedCube  {
            if(c.width==0f || c.height==0f || c.depth==0f) {
                val min = VectorF(c.min.dimension) {if(c.max[it]-c.min[it]==0f) c.min[it]-minv else c.min[it]}
                val max = VectorF(c.min.dimension) {if(c.max[it]-c.min[it]==0f) c.max[it]+minv else c.max[it]}
                return AlignedCube(min, max)
            } else
                return c
        }
        fun ensureVolume(v1:VectorF, v2:VectorF, minv:Float=1e-9f) : AlignedCube = ensureVolume(from(v1, v2), minv)
    }
}

class Triangle(val p0:VectorF, val p1:VectorF, val p2:VectorF) {
    override fun toString(): String = "Triangle($p0, $p1, $p2)"

    companion object {
        fun around(v:VectorF, h:Float) : Triangle = Triangle(VectorF(v.x-h/2f, v.y-h/2f, v.z), VectorF(v.x, v.y+h/2f, v.z), VectorF(v.x+h/2f, v.y-h/2f, v.z))
    }
}

class Circle(val center:VectorF, val radius:Float)

class Ray(val origin:VectorF, val dir:VectorF) {
    val inverse : VectorF = MutableVectorF(1/dir.x, 1/dir.y, 1/dir.z)
    val invneg : Array<Boolean> = Array(inverse.dimension) {it<0}
}

abstract class Curve(val start:VectorF, val end:VectorF, val cps:Iterable<VectorF>) {
    abstract fun at(t:Float) : VectorF

}

class CubicBezier(start:VectorF, end:VectorF, cps:Iterable<VectorF>) : Curve(start, end, cps) {
    init {
        assert(cps.count()==2)
    }

    override fun at(t: Float): VectorF = bezier(start, cps.elementAt(0), cps.elementAt(1), end, t)

    fun bezier(a:VectorF, b:VectorF, c:VectorF, d:VectorF, t:Float) : VectorF {
        val e = a.mix(b, t)
        val f = b.mix(c, t)
        val g = c.mix(d, t)
        val h = e.mix(f, t)
        val i = f.mix(g, t)
        return h.mix(i, t)
    }

}

class QuadBezier(start:VectorF, end:VectorF, cps:Iterable<VectorF>) : Curve(start, end, cps) {
    init {
        assert(cps.count()==1)
    }

    override fun at(t: Float): VectorF = bezier(start, cps.elementAt(0), end, t)

    fun bezier(a:VectorF, b:VectorF, c:VectorF, t:Float) : VectorF {
        val d = a.mix(b, t)
        val e = b.mix(c, t)
        return d.mix(e, t)
    }

}