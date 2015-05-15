package ch.passenger.kotlin.graphics.util.collections

import java.util.*

/**
 * svd coded this on 15/05/2015.
 */
class Hierarchy<T> {
    private class Node<T>(val value:T) {
        val children:MutableList<Node<T>> = arrayListOf()

        fun find(t:T) : Node<T>? {
            if(value==t) return this
            for(c in children) {
                val cand = c.find(t)
                if(cand!=null) return cand
            }
            return null
        }

        fun remove(t: T): Node<T>? {
            if(t==value) return this
            val cn = children.firstOrNull { it.value == t }
            if (cn != null) {
                children.remove(cn)
                return cn
            }
            for(c in children) {
                val acn = c.remove(t)
                if(acn!=null) return acn
            }
            return null
        }
        fun contains(t:T) : Boolean {
            if(value==t) return true
            return children.any{t in it}
        }

        fun level(t:T, lvl:Int) : Int{
            if(t==value) return lvl
            for(c in children) {
                val cl = c.level(t, lvl + 1)
                if(cl >=0) return cl
            }
            return -1
        }

        fun path(t:T) : List<T> {
            if(t==value) return listOf(t)
            for(c in children) {
                val cp = c.path(t)
                if(cp.size()>0) {
                    val res = arrayListOf(this.value!!)
                    res.addAll(cp)
                    return res
                }
            }
            return emptyList()
        }

        val size : Int get() = children.fold(children.size()){acc,it->acc+it.size}

        fun flat() : Iterable<Node<T>> = listOf(this) + children.flatMap { it.flat() }
        fun<U> collect(u:U, cb:(U,T)->U) {
            val nu = cb(u, value)
            children.forEach { it.collect(nu, cb) }
        }
        val depth : Int = 1+(children.maxBy { it.depth }?.depth?:0)

    }
    private val children: ArrayList<Node<T>> = arrayListOf()

    private fun remove(t:T) : Node<T>? {
        var rem : Node<T>? = null
        for(c in children) {
            if(c.value==t) {
                rem = c
                break
            }
            val n = c.remove(t)
            if(n!=null) return n
        }
        if(rem!=null) children.remove(rem)

        return rem
    }

    private fun insert(n:Node<T>)  {
        children add n
    }

    fun add(t:T) {if(t !in this) children add Node(t)}

    private fun find(t:T) : Node<T>? {
        for(c in children) {
            if(c.value==t) return c
            val f = c.find(t)
            if(f!=null) return f
        }
        return null
    }

    fun isIn(a:T, b:T) {
        val nb = find(b)

        if(nb!=null) {
            assert(a!=nb.value)
            if(a in nb) return
            val na = find(a)
            if(na!=null) {
                na.flat().map { it.value }.filterNotNull().forEach { nb.remove(it) }
                children.remove(na)
                nb.children.add(na)
            } else nb.children add Node(a)
        } else {
            var na = find(a)
            children.remove(na)
            if (na == null) na = Node(a)
            val nb = Node(b)
            insert(nb)
            nb.children.add(na)
        }
    }

    fun level(t:T) : Int {
        for(c in children) {
            val cl = c.level(t, 0)
            if(cl >= 0) return cl
        }
        return -1
    }

    fun path(t:T) : List<T> {
        for(c in children) {
            val p = c.path(t)
            if(p.size()>0) return p
        }
        return emptyList()
    }

    fun children(t:T) : List<T> {
        val n = find(t)
        if(n!=null) return n.children.map { it.value }
        return emptyList()
    }

    fun root() : List<T> = children.map { it.value }
    val size : Int get() = children.fold(children.size()) {acc, it -> acc+it.size}
    fun contains(t:T) : Boolean = children.any {t in it}

    fun<U> collect(u:U, cb:(U,T)->U) {
        children.forEach{it.collect(u, cb)}
    }
    val maxdepth : Int get() = (children.maxBy { it.depth }?.depth?:0)
    val maxlevel : Int get() = maxdepth -1
    fun leveled(cb:(Int,T)->Unit) {
        collect(0) {
            l, t -> cb(l, t); l+1
        }
    }
}