package ch.passenger.kotlin.graphics.javafx.util

import javafx.beans.property.Property
import javafx.event.Event
import javafx.event.EventType
import javafx.scene.Node
import rx.Observable
import rx.Subscriber
import rx.subscriptions.Subscriptions

/**
 * Created by svd on 08/05/2015.
 */
fun<T:Any> Property<T>.invoke() : T = this.getValue()
fun<T:Any> Property<T>.invoke(t:T)  = this.setValue(t)

fun<T: Event> Node.fromEvents(etype: EventType<T>) : Observable<T> = Observable.create (
        object : Observable.OnSubscribe<T> {
            override fun call(sub: Subscriber<in T>) {
                val cb: (T) -> Unit = {
                    sub.onNext(it)
                }
                this@fromEvents.addEventHandler(etype, cb)
                sub.add(Subscriptions.create { this@fromEvents.removeEventHandler(etype, cb) })
            }
        }
)
