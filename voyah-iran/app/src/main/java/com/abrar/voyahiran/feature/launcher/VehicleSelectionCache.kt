package com.abrar.voyahiran.feature.launcher

/** OEM-style UI model: real feedback and pending selections are never the same data. */
internal class VehicleSelectionCache<S>(initial:S) {
    class Request<S>(val key:String, val apply:(S)->S, val matches:(S)->Boolean) {
        var dispatched=true
    }
    private var actual=initial
    private val requests=linkedMapOf<String,Request<S>>()
    val display:S get()=requests.values.fold(actual){s,r->r.apply(s)}
    fun updateActual(transform:(S)->S){actual=transform(actual)}
    fun request(key:String,apply:(S)->S,matches:(S)->Boolean):Request<S> =
        Request(key,apply,matches).also{requests[key]=it}
    fun accept(state:S){actual=state;requests.values.removeAll{it.dispatched && it.matches(state)}}
    fun pending(request:Request<S>)=requests[request.key]===request
    fun finish(request:Request<S>){if(pending(request))requests.remove(request.key)}
}
