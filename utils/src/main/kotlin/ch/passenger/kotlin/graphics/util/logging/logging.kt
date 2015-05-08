package ch.passenger.kotlin.graphics.util.logging

/**
 * Created by svd on 07/05/2015.
 */
class Configure() {
    init {
        val st = javaClass.getResourceAsStream("/logging.properties")
        java.util.logging.LogManager.getLogManager().readConfiguration(st)
    }
}