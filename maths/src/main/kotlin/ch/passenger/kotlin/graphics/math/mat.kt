package ch.passenger.kotlin.graphics.math

import java.nio.FloatBuffer

/**
 * Created by svd on 04/04/2015.
 */
class MatrixF(val cols:Int, val rows:Int,init:  (Int,Int) -> Float = { c, r -> if(r==c) 1f else 0f}) : Collection<Float> {
    val m: Array<Float>;

   init {
        /*
        00 01 02
        10 11 12
        20 21 22

        00 10 20 01 11 21 02 12 22
        0  1  2  3  4  5  6  7  8

        eg: 8 / 3 = 2
        8 - 2*3 = 2    -> (2,2)

        5/3 = 1
        5 - 1*3 = 2 -> (2,1)
         */
        m = Array(rows * cols) {
            val c = it / rows;
            val r = it % rows; init(c, r)
        }
    }

    fun get(c: Int, r: Int) : Float {
        assert((c * rows + r)<m.size(), "$r $c = ${c * rows + r} ($rows, $cols)")
        //Mij = m[i+M*j]
        return m[c * rows + r]
    }
    fun set(c: Int, r: Int, v: Float) {
        assert((c * rows + r)<m.size(), "$r $c ${c * rows + r}")
        m[c * rows + r] = v
    }

    fun row(r:Int) : VectorF = VectorF(cols) { assert(r<rows, "r $r"); assert(it<cols, "c $it");this[it,r]}
    fun row(r:Int, v: VectorF)  = (0..cols-1).forEach { this[it, r] = v[it] }
    fun col(c:Int) : VectorF = VectorF(rows) {assert(c<cols, "$c col out of bounds $cols"); assert(it<rows, "$it row out $rows"); this[c, it]}
    fun col(c:Int, v: VectorF)  = (0..rows-1).forEach { this[c, it] = v[it] }
    fun get(c:Int) = col(c)
    fun set(c:Int, v: VectorF) = col(c, v)

    constructor(m: MatrixF) : this(m.cols, m.rows, { c, r -> m[c, r] }) {}
    constructor(cols:Int, rows:Int, vararg va:Float) : this(cols, rows, {c,r -> va[c*rows+r]}) {}

    fun times(sc:Float) : MatrixF = MatrixF(cols, rows) {r,c -> this[r,c]*sc}

    //If the number of columns in A does not match the number of rows in B,
    // if A is an n × m matrix and B is an m × p matrix, their matrix product AB is an n × p matrix
    // Anm Bmp = Cnp
    /*
    fun times(that:MatrixF) : MatrixF = MatrixF(this.cols, that.rows) {c,r -> assert(this.rows==that.cols);
        val row = this.row(r)
        val col = that.col(c)
        val res = row * col
        res
    }
    */
    fun times(that:MatrixF) : MatrixF = mul(that)

    private fun mul(that:MatrixF) : MatrixF {
        assert(this.rows==that.cols)
        val res = MatrixF(this.cols, that.rows)
        //Amn * Bnp = Cmp
        for(i in 0..res.cols-1) {
            for(j in 0..res.rows-1) {
                var ij = 0f
                for(k in 0..this.rows-1) {
                    val rs = this[i, k]
                    val ct = that[k, j]
                    ij += rs * ct
                }
                res[i,j] = ij
            }
        }
        return res
    }


    /*
    A33 V31 = C31
    [                       [
    m00 m01 m02       v1      v1m00 + v2m01 + v3m02
    m10 m11 m12    *  v2 =    v1m10 + v2m11 + v3m12
    m20 m21 m22       v3      v1m20 + v2m21 + v3m22
    ]
     */
    fun times(v: VectorF) : VectorF {
        val t = if(v.dimension==3) v.widen(1f) else v
        val res  = MutableVectorF(t.dimension) {0f}
        for(i in 0..t.dimension-1) {
            for(c in 0..cols-1) {
                res[i] = res[i] + t[c] * this[c,i]
            }
        }
        return res.immutable()
    }


    fun invoke(v: VectorF) : VectorF = (this*v)

    fun transpose() : MatrixF = MatrixF(rows, cols) {c,r -> this[r,c]}

    override fun size(): Int = m.size()

    override fun isEmpty(): Boolean = m.isEmpty()

    override fun contains(o: Any?): Boolean = m.contains(o)

    override fun iterator(): Iterator<Float> = m.iterator()

    override fun containsAll(c: Collection<Any?>): Boolean = m.asList().containsAll(c)

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[")
        for(i in 1..rows) {
            sb.append(row(i-1)).append("\n")
        }
        sb.append("]")

        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if(other is MatrixF && other.rows==rows && other.cols==cols) {
            for(r in 1..rows) {
                for(c in 1..cols) {
                    if(this[r-1,c-1]!=other[r-1,c-1]) return false
                }
            }
            return true
        }

        return false
    }

    override fun hashCode(): Int =  m.fold(0) {acc, f -> acc xor f.hashCode()}

    fun store(fb:FloatBuffer) = m.forEach { fb.put(it) }

    fun inverse44() : MatrixF? {
        assert(rows==4 && cols == 4)
        val a  = this
        var s0 = a[0, 0] * a[1, 1] - a[1, 0] * a[0, 1];
        var s1 = a[0, 0] * a[1, 2] - a[1, 0] * a[0, 2];
        var s2 = a[0, 0] * a[1, 3] - a[1, 0] * a[0, 3];
        var s3 = a[0, 1] * a[1, 2] - a[1, 1] * a[0, 2];
        var s4 = a[0, 1] * a[1, 3] - a[1, 1] * a[0, 3];
        var s5 = a[0, 2] * a[1, 3] - a[1, 2] * a[0, 3];

        var c5 = a[2, 2] * a[3, 3] - a[3, 2] * a[2, 3];
        var c4 = a[2, 1] * a[3, 3] - a[3, 1] * a[2, 3];
        var c3 = a[2, 1] * a[3, 2] - a[3, 1] * a[2, 2];
        var c2 = a[2, 0] * a[3, 3] - a[3, 0] * a[2, 3];
        var c1 = a[2, 0] * a[3, 2] - a[3, 0] * a[2, 2];
        var c0 = a[2, 0] * a[3, 1] - a[3, 0] * a[2, 1];

        // Should check for 0 determinant
        var invdet = 1.0f / (s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0);
        if(invdet == 0f) return null

        var b = MatrixF(4,4);

        b[0, 0] = ( a[1, 1] * c5 - a[1, 2] * c4 + a[1, 3] * c3) * invdet;
        b[0, 1] = (-a[0, 1] * c5 + a[0, 2] * c4 - a[0, 3] * c3) * invdet;
        b[0, 2] = ( a[3, 1] * s5 - a[3, 2] * s4 + a[3, 3] * s3) * invdet;
        b[0, 3] = (-a[2, 1] * s5 + a[2, 2] * s4 - a[2, 3] * s3) * invdet;

        b[1, 0] = (-a[1, 0] * c5 + a[1, 2] * c2 - a[1, 3] * c1) * invdet;
        b[1, 1] = ( a[0, 0] * c5 - a[0, 2] * c2 + a[0, 3] * c1) * invdet;
        b[1, 2] = (-a[3, 0] * s5 + a[3, 2] * s2 - a[3, 3] * s1) * invdet;
        b[1, 3] = ( a[2, 0] * s5 - a[2, 2] * s2 + a[2, 3] * s1) * invdet;

        b[2, 0] = ( a[1, 0] * c4 - a[1, 1] * c2 + a[1, 3] * c0) * invdet;
        b[2, 1] = (-a[0, 0] * c4 + a[0, 1] * c2 - a[0, 3] * c0) * invdet;
        b[2, 2] = ( a[3, 0] * s4 - a[3, 1] * s2 + a[3, 3] * s0) * invdet;
        b[2, 3] = (-a[2, 0] * s4 + a[2, 1] * s2 - a[2, 3] * s0) * invdet;

        b[3, 0] = (-a[1, 0] * c3 + a[1, 1] * c1 - a[1, 2] * c0) * invdet;
        b[3, 1] = ( a[0, 0] * c3 - a[0, 1] * c1 + a[0, 2] * c0) * invdet;
        b[3, 2] = (-a[3, 0] * s3 + a[3, 1] * s1 - a[3, 2] * s0) * invdet;
        b[3, 3] = ( a[2, 0] * s3 - a[2, 1] * s1 + a[2, 2] * s0) * invdet;

        return b;
    }

    fun det() : Float {
        assert((this.rows==2 && this.cols==2) || (this.rows==3 && cols==3))

        if(rows==2 && cols==2) {
            return this[0, 0]*this[1,1] - this[0,1]*this[1,0]
        } else if(rows==3 && cols==3) {
            val deta = this[1,1]*this[2,2]-this[1,2]*this[2,1]
            val detb = this[1,0]*this[2,2]-this[1,2]*this[2,0]
            val detc = this[1,0]*this[2,1]-this[1,1]*this[2,0]
            return this[0,0]*deta-this[0, 1]*detb+this[0,2]*detc
        }

        throw IllegalStateException()
    }

    companion object o {
        fun scale(f:Float) : MatrixF = scale(VectorF(f, f, f, 1))

        fun scale(v: VectorF): MatrixF {
            val sv = if (v.dimension < 4) v.widen(1f) else v
            return MatrixF(4, 4) { r, c -> if (r == c) sv[r] else 0f }
        }


        fun rotate(theta: Float, axis: VectorF): MatrixF {
            val c = Math.cos(theta.toDouble()).toFloat()
            val s = Math.sin(theta.toDouble()).toFloat()
            val m = MatrixF(4, 4)
            val a = if (axis.dimension < 4) axis.widen(1f) else axis
            val omc = 1 - c
            val x = a.x;
            val y = a.y;
            val z = a.z;
            val x2 = a.x * a.x;
            val y2 = a.y * a.y;
            val z2 = a.z * a.z

            m.col(0, VectorF((x2 * omc + c), (y * x * omc + z * s), (x * z * omc - y * s), (0)))
            m.col(1, VectorF((x * y * omc - z * s), (y2 * omc + c), (y * z * omc + x * s), (0)));
            m.col(2, VectorF((x * z * omc + y * s), (y * z * omc - x * s), (z2 * omc + c), (0)))
            m.col(3, VectorF((0), (0), (0), (1)))

            return m
        }

        fun translate(v: VectorF): MatrixF {
            assert(v.dimension > 2)
            val m = MatrixF(4, 4)
            m.col(3, if (v.dimension == 3) v.widen(1f) else v)
            m[3, 3] = 1f
            return m
        }


        fun perspective(fov: Float, aspect: Float, zNear: Float, zFar: Float): MatrixF {
            fun coTangent(f: Float): Float = Math.tanh(f.toDouble()).toFloat()
            fun degreesToRadians(d: Float): Float = Math.toRadians(d.toDouble()).toFloat()
            val q = 1f / Math.tan(Math.toRadians(fov.toDouble() / 2.0)).toFloat()
            val A = q / aspect
            val B = (zNear + zFar) / (zNear - zFar)
            val C = (2f * zNear * zFar) / (zNear - zFar)

            val m = MatrixF(4, 4)
            m[0] = VectorF(A, 0f, 0f, 0f)
            m[1] = VectorF(0f, q, 0f, 0f)
            m[2] = VectorF(0f, 0f, B, -1f)
            m[3] = VectorF(0f, 0f, 0f, 0f)
            return m
        }


        public fun lookAt(position: VectorF, centre: VectorF, up: VectorF): MatrixF {
            val f = (centre - position).normalise()
            val upN = up.normalise()
            val s = f.cross(upN)
            val u = s.cross(f)

            var m = MatrixF(4, 4)
            m.col(0, VectorF(s.x, u.x, -f.x, 0))
            m.col(1, VectorF(s.y, u.y, -f.y, 0))
            m.col(2, VectorF(s.z, u.z, -f.z, 0))
            m.col(1, VectorF(0f, 0f, 0f, 1f))
            return m
        }

        public fun lookAt2(position: VectorF, centre: VectorF, up: VectorF): MatrixF {
            // Compute direction from position to lookAt
            var dirX = centre.x - position.x;
            var dirY = centre.y - position.y;
            var dirZ = centre.z - position.z;
            // Normalize direction
            val dirLength = VectorF.distance(position, centre);
            dirX /= dirLength;
            dirY /= dirLength;
            dirZ /= dirLength;
            // Normalize up
            val upN = up.normalise()
            // right = direction x up
            val rightX = dirY * upN.z - dirZ * upN.y;
            val rightY = dirZ * upN.x - dirX * upN.z;
            val rightZ = dirX * upN.y - dirY * upN.x;
            // up = right x direction
            val upX = rightY * dirZ - rightZ * dirY;
            val upY = rightZ * dirX - rightX * dirZ;
            val upZ = rightX * dirY - rightY * dirX;

            val m = MatrixF(4, 4)
            m[0, 0] = rightX
            m[0, 1] = upX
            m[0, 2] = -dirX
            m[0, 3] = 0.0.toFloat()
            m[1, 0] = rightY
            m[1, 1] = upY
            m[1, 2] = -dirY
            m[1, 3] = 0.0.toFloat()
            m[2, 0] = rightZ
            m[2, 1] = upZ
            m[2, 2] = -dirZ
            m[2, 3] = 0.0.toFloat()
            m[3, 0] = -rightX * position.x - rightY * position.y - rightZ * position.z
            m[3, 1] = -upX * position.x - upY * position.y - upZ * position.z
            m[3, 2] = dirX * position.x + dirY * position.y + dirZ * position.z
            m[3, 3] = 1.0.toFloat()
            return m
        }
    }
}