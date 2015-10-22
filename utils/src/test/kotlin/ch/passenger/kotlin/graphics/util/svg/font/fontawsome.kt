package ch.passenger.kotlin.graphics.util.svg.font

import org.testng.annotations.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Created by svd on 07/05/2015.
 */
class FontAwesomeTest {
    @Test
    fun testNameMapper() {
        val res = FontawesomeNameMapper::class.javaClass.getResourceAsStream("/fontawesome/_variables.scss")
        FontawesomeNameMapper(BufferedReader(InputStreamReader(res)))
        assert(true)
    }
}