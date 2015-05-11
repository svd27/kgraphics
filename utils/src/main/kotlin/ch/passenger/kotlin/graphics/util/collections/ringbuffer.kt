package ch.passenger.kotlin.graphics.util.collections

/**
 * Created by svd on 07/05/2015.
 */
class RingBuffer<T>(val length:Int) : Iterable<T> {
    private class Node<T>(val t:T) {var next:Node<T> = this}
    var head : Node<T>? = null

    val size : Int get() {
        if(head==null) return 0
        var cn = head!!
        var c = 0
        do {
            c++
            cn = cn.next
        } while(cn!=head)
        return c
    }

    fun push(t:T) {
        if(head==null) {
            head = Node(t)
        } else {
            val nh = Node(t)
            nh.next = head!!
            if(size==length) {
                var prev = head!!
                var cn = head!!.next
                while(cn.next!=head) {
                    prev=cn
                    cn = cn.next
                }
                prev.next = nh
            } else {
                var cn = head!!.next
                while(cn.next!=head) cn = cn.next
                cn.next = nh
            }
            head = nh
        }
    }

    fun get(idx:Int) : T? {
        if(length==0) return null
        if(idx==0) return head!!.t
        var cn = head!!
        for(i in 0..idx-1) cn = cn.next
        return cn.t
    }

    fun clear() {
        head = null
    }

    override fun iterator(): Iterator<T> = object: Iterator<T> {
        var idx = 0
        override fun hasNext(): Boolean = idx < length

        override fun next(): T = get(idx++)!!
    }
}