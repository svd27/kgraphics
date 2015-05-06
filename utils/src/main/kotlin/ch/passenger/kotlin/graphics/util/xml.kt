package ch.passenger.kotlin.graphics.util

import org.slf4j.LoggerFactory
import java.util.*
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter
import kotlin.reflect
import kotlin.reflect.KClass


/**
 * Created by svd on 05/05/2015.
 */
trait XMLWritableFactory<T> {
    val name:String
    fun readXML(r:XMLStreamReader) : T
}

trait XMLWritable<T> {
    fun writeXML(wr:XMLStreamWriter)
}

public object XMLWriteableRegistry {
    val log = LoggerFactory.getLogger(XMLWriteableRegistry.javaClass)
    val k : KClass<XMLWritableFactory<kotlin.Any>>? = null
    val factories : MutableMap<KClass<*>, XMLWritableFactory<*>> = HashMap()
    val toplevel : MutableMap<String,KClass<*>> = hashMapOf()

    public fun<T:XMLWritable<T>> register(k:KClass<T>, f:XMLWritableFactory<*>) {
        factories[k] = f
    }

    public fun<T:XMLWritable<T>> registerToplevel(k:KClass<T>, element:String) {
        toplevel[element] = k
    }

    fun<T> read(r:XMLStreamReader, k:KClass<XMLWritable<*>>) : T where T:XMLWritable<Any> {
        if(k !in factories) throw IllegalStateException()
        val res = factories[k]!!.readXML(r)
        return res as T
    }

    fun read(r:XMLStreamReader) : Iterable<Any> {
        val res = arrayListOf<Any>()
        while(r.next()!=XMLStreamConstants.END_DOCUMENT) {
            when(r.getEventType()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val n = r.getLocalName()
                    val f = factories.values().firstOrNull { it.name==n }
                    if(f!=null) res.add(f.readXML(r)) else if(n in toplevel) {
                        res add readCollection(r, toplevel[n]!!)
                    }
                    else {
                        gobble(n, r)
                    }
                }
            }
        }

        return res
    }


    fun<T> readCollection(r:XMLStreamReader, kc:KClass<T>) : Iterable<T> {
        val res = arrayListOf<T>()
        val f = factories[kc]!!
        while(r.next()!=XMLStreamConstants.END_ELEMENT) {
            when(r.getEventType()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val n = r.getLocalName()

                    if(f.name==r.getLocalName()) res.add(f.readXML(r) as T) else {
                        gobble(n, r)
                    }
                }
            }
        }

        return res
    }

    fun<T:XMLWritable<T>> write(w : XMLStreamWriter, c:Iterable<T>, parent:String) {
        w.writeStartElement(parent)
        c.forEach { it.writeXML(w) }
        w.writeEndElement()
    }



    fun gobble(n:String, r:XMLStreamReader) {
        log.warn("skipping ${r.getLocalName()}")
        while(r.next()!=XMLStreamConstants.END_ELEMENT) {
            if(r.getEventType()==XMLStreamConstants.START_ELEMENT) {
                gobble(r.getLocalName(), r)
            }
        }
    }
}

fun XMLStreamReader.attributes(cb:(ns:String,n:String,v:String)->Unit) {
    val c = getAttributeCount()-1
    for(i in 0..c) {
        val ns = getAttributeNamespace(i)
        val n = getAttributeLocalName(i)
        val v = getAttributeValue(i)
        cb(ns, n, v)
    }
}

data class AttDesc(val ns:String?, val n:String, val v:String)

val XMLStreamReader.attributes : Iterable<AttDesc> get() =
    Array(getAttributeCount()) { i ->
        val ns = getAttributeNamespace(i)
        val n = getAttributeLocalName(i)
        val v = getAttributeValue(i)
        AttDesc(ns, n, v)
    }.toList()


fun XMLStreamReader.attribute(local:String) : String = attributes.first { it.n==local }.v