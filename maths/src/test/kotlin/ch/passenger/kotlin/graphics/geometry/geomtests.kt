package ch.passenger.kotlin.graphics.geometry

import ch.passenger.kotlin.graphics.math.VectorF
import org.testng.annotations.*
import org.testng.annotations.Test

/**
 * Created by svd on 06/05/2015.
 */

class GeomTests {
    @Test
    fun testCubeIntersect() {
        val cube = AlignedCube(VectorF(-1, -1, -1), VectorF(1, 1, 1))
        val ray = Ray(VectorF(0, 0, -2), VectorF(0, 0, 2))
        val inter = cube.intersect(ray, 0f, 1f)
        println("inter: $inter")
        val ray1 = Ray(VectorF(-3, -3, -3), VectorF(3, 3, 3))
        val inter1 = cube.intersect(ray1, 0f, 1f)
        println("inter1 : $inter1")
        assert(true==false)
    }

}