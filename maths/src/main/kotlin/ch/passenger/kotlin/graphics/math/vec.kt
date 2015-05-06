package ch.passenger.kotlin.graphics.math

import ch.passenger.kotlin.graphics.util.*
import java.nio.FloatBuffer
import javax.xml.XMLConstants
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter
import javax.xml.stream.events.XMLEvent

/**
 * Created by svd on 04/04/2015.
 */


fun Float.sqrt(): Float = Math.sqrt(this.toDouble()).toFloat()


trait VectorF : Comparable<VectorF>, XMLWritable<MutableVectorF> {
    public val dimension:Int
    public val x : Float get() = get(0)
    public val y: Float  get() = this[1]
    public val z: Float  get() = this[2]
    public val w: Float  get() = this[3]

    fun get(i:Int) : Float
    fun mutable() : MutableVectorF = MutableVectorF(this)
    fun invoke() : Iterable<Float> = Array(dimension) {this[it]}.toList()
    override fun compareTo(other: VectorF): Int {
        if(dimension!=other.dimension) throw IllegalStateException("$dimension != ${other.dimension}")
        for(i in 0..dimension-1) {
            val cm = this[i].compareTo(other[i])
            if(cm!=0) return cm
        }
        return 0
    }
    fun toArray(): Array<Float> = Array(dimension) {this[it]}
    fun times(f: Float): VectorF = VectorF.create(dimension) { this[it] * f }
    fun minus(): VectorF = times(-1f)
    fun plus(v: VectorF): VectorF = create(dimension) { this[it] + v[it] }
    fun minus(v: VectorF): VectorF = create(dimension) { this[it] - v[it] }
    fun magnitude(): Float = this().map { it * it }.foldRight(0f) { l, r -> l + r }.sqrt()
    fun times(v: VectorF): Float = this().merge(v()) { f1, f2 -> f1 * f2 }.foldRight(0f) { acc, c -> acc + c }
    fun times(m:MatrixF) : MatrixF {
        val row = MatrixF(1, dimension) {c, r -> this[r]}
        return row*m
    }

    fun invoke(m:MatrixF) : VectorF = (this*m).col(0)

    fun normalise(): VectorF {
        val m = magnitude()
        assert(m > 0f, "magnitude $m in $this")
        return create(dimension) { this[it] / m }

    }

    fun cross(v: VectorF): VectorF = create(dimension) {
        assert(dimension==3)
        when (it) {
            0 -> this.y*v.z - this.z*v.y
            1 -> this.z*v.x - this.x*v.z
            2 -> this.x*v.y - this.y*v.x
            else -> throw IllegalStateException()
        }
    }

    fun reflect(around:VectorF) :VectorF = around*2f-this

    fun widen(v:Float=1f) : VectorF = create(dimension+1) {if(it<dimension) this[it] else v}

    override fun equals(other: Any?): Boolean {
        if (other is VectorF && other.dimension == dimension) {
            return this().merge(other()) { f1, f2 -> f1 == f2 }.all { it }
        }
        return false
    }

    fun equals(other: Any?, epsilon:Float): Boolean {
        if (other is VectorF && other.dimension == dimension) {
            return this().merge(other()) { f1, f2 -> Math.abs(f1-f1) <= epsilon }.all { it }
        }
        return false
    }
    fun line(v1: VectorF) : LineSegment = LineSegment.create(this, v1)

    override fun writeXML(wr: XMLStreamWriter) {
        wr.writeStartElement(element)
        wr.writeAttribute("values", this().joinToString(","))
        wr.writeEndElement()
    }

    fun immutable() : VectorF = this


    companion object {
        private fun create(d:Int, init:(Int)->Float) : VectorF = MutableVectorF(d, init).immutable()

        fun distance(a: VectorF, b: VectorF): Float = (b - a).magnitude()
        /**
         * measure angle between three points, moving in direction p0-p1-p2
         * this is the angle to the "left" going ccw
         *
         */
        fun angle3p(p0: VectorF, p1: VectorF, p2: VectorF) : Float {
            val a = p1
            val b = p0
            val c = p2
            //p1->p0            p1->p2
            val v1 = (b-a); val v2 = (c-a)
            var ang = Math.atan2(v2.y.toDouble(),v2.x.toDouble()) - Math.atan2(v1.y.toDouble(),v1.x.toDouble())
            if(ang<0) ang += 2*Math.PI
            return ang.toFloat()
        }
        fun angle2v(v1: VectorF, v2: VectorF) : Float {
            var ang = Math.atan2(v2.y.toDouble(),v2.x.toDouble()) - Math.atan2(v1.y.toDouble(),v1.x.toDouble())
            if(ang<0) ang += 2*Math.PI
            return ang.toFloat()
        }

        /**
        http://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment
         */
        fun closest(p: VectorF, l: LineSegment): Float {
            val ll = l.dir.magnitude()
            val l2 = ll * ll
            if (l2 == 0f) return distance(p, l.start)
            val t = (p - l.start) * l.dir / l2
            if (t < 0f) return distance(p, l.start)
            else if (t > 1f) return distance(p, l.end)
            val proj = l.at(t)
            return distance(p, proj)
        }

        fun invoke(vararg ns:Number) = MutableVectorF(*ns)
        fun invoke(d:Int, init:(Int)->Float) = MutableVectorF(d, init)

        val element = "vector"

        init {

            XMLWriteableRegistry.register<MutableVectorF>(MutableVectorF::class, object : XMLWritableFactory<MutableVectorF> {
                override val name: String
                    get() = element

                override fun readXML(r: XMLStreamReader): MutableVectorF {
                    val values = r.attribute("values")
                    while(r.next()!=XMLStreamConstants.END_ELEMENT) {}
                    val vs = values.split(",")
                    return MutableVectorF(vs.size()) {
                        vs[it].toFloat()
                    }
                }
            })
        }

    }
}

open class MutableVectorF(dim: Int, init: (Int) -> Float = { 0f }) : VectorF {
    init {assert(dim>0)}
    override var x: Float  get() = this[0]
        set(v) {
            this[0] = v
        }
    override var y: Float  get() = this[1]
        set(v) {
            this[1] = v
        }
    override var z: Float  get() = this[2]
        set(v) {
            this[2] = v
        }
    override var w: Float  get() = this[3]
        set(v) {
            this[3] = v
        }
    val array: Array<Float> = Array(dim) { init(it) }
    init {assert(array.size()>0)}
    override val dimension: Int = array.size()


    constructor(fa: Array<Float>) : this(fa.size(), { fa[it] }) {}

    constructor(fi: Collection<Float>) : this(fi.size(), { fi.elementAt(it) }) {}

    constructor(vararg fs: Number) : this(fs.size(), { fs[it].toFloat() }) {}

    constructor(iv: VectorF) : this(iv.dimension, {iv[it]}) {}

    override fun get(i: Int): Float = array[i]
    fun set(i: Int, f: Float) = array.set(i, f)
    fun set(v: MutableVectorF) = assign(v)
    fun assign(v: MutableVectorF)  {
        assert(dimension==v.dimension)
        for(i in 0..dimension-1) this[i] = v[i]
    }
    override fun toArray(): Array<Float> = array.clone()
    override fun invoke(): Iterable<Float> = object : Iterable<Float> {
        override fun iterator(): Iterator<Float> = object: Iterator<Float> {
            var idx = -1
            override fun next(): Float = array[++idx]

            override fun hasNext(): Boolean = idx+1<dimension
        }
    }

    fun timesAssign(f: Float): Unit =array.forEachIndexed { i, fl -> array[i] *= fl }
    fun plusAssign(v: MutableVectorF) {assert(v.dimension==dimension); v().forEachIndexed { i, f -> this[i] += f }}






    fun store(b:FloatBuffer) {
        this().forEach { b.put(it) }
    }

    override fun hashCode(): Int = this().fold(0) { acc, c -> acc xor c.hashCode()}

    override fun toString(): String = this().joinToString(",", "[", "]")


    override fun immutable() : VectorF = object : VectorF by this {}
}


enum class IntersectionType {INTERSECT NOPE  COINCIDENT}
data open class Intersection(val incident: VectorF, val tray:Float, val tsegment:Float, val type:IntersectionType)
val NOPE = object : Intersection(MutableVectorF(0, 0, 0), 0f, 0f, IntersectionType.NOPE) {}
val PARALLEL = object : Intersection(MutableVectorF(0, 0, 0), 0f, 0f, IntersectionType.NOPE) {}
/*
// Denominator for ua and ub are the same, so store this calculation
         double d =
            (L2.Y2 - L2.Y1) * (L1.X2 - L1.X1)
            -
            (L2.X2 - L2.X1) * (L1.Y2 - L1.Y1);

         //n_a and n_b are calculated as seperate values for readability
         double n_a =
            (L2.X2 - L2.X1) * (L1.Y1 - L2.Y1)
            -
            (L2.Y2 - L2.Y1) * (L1.X1 - L2.X1);

         double n_b =
            (L1.X2 - L1.X1) * (L1.Y1 - L2.Y1)
            -
            (L1.Y2 - L1.Y1) * (L1.X1 - L2.X1);

         // Make sure there is not a division by zero - this also indicates that
         // the lines are parallel.
         // If n_a and n_b were both equal to zero the lines would be on top of each
         // other (coincidental).  This check is not done because it is not
         // necessary for this implementation (the parallel check accounts for this).
         if (d == 0)
            return false;

         // Calculate the intermediate fractional point that the lines potentially intersect.
         double ua = n_a / d;
         double ub = n_b / d;

         // The fractional point will be between 0 and 1 inclusive if the lines
         // intersect.  If the fractional calculation is larger than 1 or smaller
         // than 0 the lines would need to be longer to intersect.
         if (ua >= 0d && ua <= 1d && ub >= 0d && ub <= 1d)
         {
            ptIntersection.X = L1.X1 + (ua * (L1.X2 - L1.X1));
            ptIntersection.Y = L1.Y1 + (ua * (L1.Y2 - L1.Y1));
            return true;
         }
         return false;
 */

fun rayIntersect(O: VectorF, dir: VectorF, la : VectorF, lb: VectorF) : Intersection {
    val a = O; val b=O+dir
    val denom = (lb.y - la.y) * (b.x - a.x) - (lb.x - la.x) * (b.y - a.y);

    val na = (lb.x - la.x) * (a.y - la.y) - (lb.y - la.y) * (a.x - la.x);
    val nb = (b.x - a.x) * (a.y - la.y) - (b.y - a.y) * (a.x - la.x);

    if (denom != 0f) {
        val ua = na / denom;
        val ub = nb / denom;
        val i = la + (lb-la)*ub
        if (ua >= 0.0f && ub >= 0f && ub <= 1f) {
            return Intersection(i, ua, ub, IntersectionType.INTERSECT)
        } else {
            return NOPE
        }
    } else {
        if (na == 0f && nb == 0f) {
            return Intersection(a, 1f, 1f, IntersectionType.COINCIDENT)
        } else {
            return PARALLEL
        }
    }
}


trait LineSegment {
    val start: VectorF; val end: VectorF
    val dir : VectorF get() = end-start
    fun at(t:Float): VectorF = start + dir*t

    fun intersects2D(line:LineSegment) : Intersection {
        val la = line.start; val lb = line.end
        val a = start; val b=end
        val denom = (lb.y - la.y) * (b.x - a.x) - (lb.x - la.x) * (b.y - a.y);

        val na = (lb.x - la.x) * (a.y - la.y) - (lb.y - la.y) * (a.x - la.x);
        val nb = (b.x - a.x) * (a.y - la.y) - (b.y - a.y) * (a.x - la.x);

        if (denom != 0f) {
            val ua = na / denom;
            val ub = nb / denom;
            val i = la + (lb-la)*ub
            if (ua >= 0.0f && ub >= 0f && ub <= 1f) {
                return Intersection(i, ua, ub, IntersectionType.INTERSECT)
            } else {
                return NOPE
            }
        } else {
            if (na == 0f && nb == 0f) {
                if(VectorF.closest(start, line)==0f)
                return Intersection(a, 1f, 1f, IntersectionType.COINCIDENT)
                else return PARALLEL
            } else {
                return PARALLEL
            }
        }
    }

    companion object o {
        fun create(v0: VectorF,v1: VectorF) : LineSegment = object : LineSegment {
            override val start: VectorF = v0
            override val end: VectorF = v1
        }
    }
}

trait Rectangle2D {
    val min: VectorF; val max: VectorF;
    val x : Float get() = min.x
    val y : Float get() = min.y
    val w : Float get() = (max-min).x
    val h : Float get() = (max-min).y
    val center : VectorF get() = min + (max-min)*.5f

    fun contains(v: VectorF) : Boolean = v.x in min.x..max.x && v.y in min.y..max.y
    fun contains(r:Rectangle2D) : Boolean = r.min in this && r.max in this
    fun intersects(r:Rectangle2D) : Boolean = min in r || max in r
    fun crossedBy(l:LineSegment) : Boolean = l.start in this || l.end in this || borders.any {
        val i = l.intersects2D(it)
        i.type in setOf(IntersectionType.INTERSECT, IntersectionType.COINCIDENT) && i.tsegment in 0f..1f && i.tray in 0f..1f
    }
    override fun equals(other: Any?): Boolean = if(other is Rectangle2D) other.min==min && other.max==max else false

    fun plus(r: Rectangle2D) : Rectangle2D {
        val minx = Math.min(min.x, r.min.x)
        val miny = Math.min(min.y, r.min.y)
        val maxx = Math.max(max.x, r.max.x)
        val maxy = Math.max(max.y, r.max.y)
        return create(MutableVectorF(minx, miny, 0), MutableVectorF(maxx, maxy, 0))
    }
    fun plus(v: MutableVectorF) : Rectangle2D {
        return this+create(v, v)
    }
    fun minus(r:Rectangle2D) : Rectangle2D {
        if(r in this) return r
        if(this in r) return this
        if(r.min in this) {
            return Rectangle2D.create(r.min, max)
        }
        if(r.max in this) {
            return Rectangle2D.create(min, r.max)
        }
        return Rectangle2D.create(MutableVectorF(0, 0, 0), MutableVectorF(0, 0, 0))
    }

    fun split() : Iterable<Rectangle2D> {
        val res = arrayListOf<Rectangle2D>()
        val mid = min + (max-min)*.5f
        res add create(min, mid)
        res add create(MutableVectorF(mid.x, min.y, 0), MutableVectorF(max.x, mid.y, 0))
        res add create(MutableVectorF(min.x, mid.y, 0), MutableVectorF(mid.x, max.y, 0))
        res add create(mid, max)
        return res
    }

    val borders : Iterable<LineSegment> get() = listOf(
            LineSegment.create(min, MutableVectorF(max.x, min.y, 0)),
            LineSegment.create(min, MutableVectorF(min.x, max.y, 0)),
            LineSegment.create(MutableVectorF(max.x, min.y, 0), max),
            LineSegment.create(MutableVectorF(min.x, max.y, 0), max)
    )

    override fun toString(): String = "(${min.x}, ${min.y} - ${(max-min).x}x${(max-min).y})"

    companion object o {
        fun create(min: VectorF, max: VectorF) : Rectangle2D = ARectangle2D(min, max)
        private class ARectangle2D(override val min: VectorF, override val max: VectorF) : Rectangle2D
    }
}
