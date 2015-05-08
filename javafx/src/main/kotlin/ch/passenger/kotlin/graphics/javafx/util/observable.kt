package ch.passenger.kotlin.graphics.javafx.util

import javafx.beans.property.Property
/**
 * Created by svd on 08/05/2015.
 */
fun<T:Any> Property<T>.invoke() : T = this.getValue()
fun<T:Any> Property<T>.invoke(t:T)  = this.setValue(t)