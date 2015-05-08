package ch.passenger.kotlin.graphics.util

import java.util.regex.Matcher
/**
 * Created by svd on 07/05/2015.
 */

fun Matcher.get(i:Int) : String = group(i)
fun Matcher.invoke() : Iterable<String> = object: Iterable<String> {
    override fun iterator(): Iterator<String> = object: Iterator<String> {
        var idx = 1
        override fun next(): String = this@invoke.group(idx++)

        override fun hasNext(): Boolean = this@invoke.groupCount()>0 && idx in 1..this@invoke.groupCount()
    }
}