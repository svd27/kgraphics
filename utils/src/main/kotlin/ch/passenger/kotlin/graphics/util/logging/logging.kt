package ch.passenger.kotlin.graphics.util.logging

import org.slf4j.Logger
import org.slf4j.Marker

/**
 * Created by svd on 07/05/2015.
 */
class Configure() {
    init {
        val st = javaClass.getResourceAsStream("/logging.properties")
        java.util.logging.LogManager.getLogManager().readConfiguration(st)
    }
}

fun Logger.dm(m:Marker, vararg args:Any, p:Logger.()->String) {
    if(isDebugEnabled(m)) {
        debug(m, p(), *args)
    }
}

fun Logger.d(vararg args:Any, p:Logger.()->String) {
    if(args.size()>0 && args.first() is Marker) dm(args.first() as Marker, *(args.copyOfRange(1, args.size())), p = p)
    else if(isDebugEnabled) {
        debug(p(), *args)
    }
}


fun Logger.im(m:Marker, vararg args:Any, p:Logger.()->String) {
    if(isInfoEnabled(m)) {
        info(m, p(), *args)
    }
}

fun Logger.i(vararg args:Any, p:Logger.()->String) {
    if(args.size()>0 && args.first() is Marker) im(args.first() as Marker, *(args.copyOfRange(1, args.size())), p = p)
    else if(isInfoEnabled) {
        info(p(), *args)
    }
}

fun Logger.wm(m:Marker, vararg args:Any, p:Logger.()->String) {
    if(isWarnEnabled(m)) {
        warn(m, p(), *args)
    }
}

fun Logger.w(vararg args:Any, p:Logger.()->String) {
    if(args.size()>0 && args.first() is Marker) wm(args.first() as Marker, *(args.copyOfRange(1, args.size())), p = p)
    else if(isWarnEnabled) {
        warn(p(), *args)
    }
}

fun Logger.em(m:Marker, vararg args:Any, p:Logger.()->String) {
    if(isErrorEnabled(m)) {
        error(m, p(), *args)
    }
}

fun Logger.e(vararg args:Any, p:Logger.()->String={""}) {
    if(args.size()>0 && args.first() is Marker) em(args.first() as Marker, *(args.copyOfRange(1, args.size())), p = p)
    else if(isErrorEnabled) {
        error(p(), *args)
    }
}

fun Logger.tm(m:Marker, vararg args:Any, p:Logger.()->String) {
    if(isTraceEnabled(m)) {
        trace(m, p(), *args)
    }
}

fun Logger.t(vararg args:Any, p:Logger.()->String) {
    if(args.size()>0 && args.first() is Marker) tm(args.first() as Marker, *(args.copyOfRange(1, args.size())), p = p)
    if(isTraceEnabled) {
        trace(p(), *args)
    }
}