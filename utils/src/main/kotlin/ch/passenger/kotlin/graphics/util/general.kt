package ch.passenger.kotlin.graphics.util

import java.util.regex.Matcher
import kotlin.reflect.KClass

/**
 * Created by svd on 07/05/2015.
 */

operator fun Matcher.get(i:Int) : String = group(i)
fun Matcher.invoke() : Iterable<String> = object: Iterable<String> {
    override fun iterator(): Iterator<String> = object: Iterator<String> {
        var idx = 1
        override fun next(): String = this@invoke.group(idx++)

        override fun hasNext(): Boolean = this@invoke.groupCount()>0 && idx in 1..this@invoke.groupCount()
    }
}

fun<T,U> Iterable<T>.cast() : Iterable<U> = map { it as U }