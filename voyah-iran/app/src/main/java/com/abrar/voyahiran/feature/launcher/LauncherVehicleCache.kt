package com.abrar.voyahiran.feature.launcher

/** Main-thread cache: confirmed vehicle data plus independent optimistic selections. */
internal class LauncherVehicleCache {
    data class Request(val id:Long,val op:String,val value:Int,val key:String)
    private var serial=0L
    private var actual=LauncherVehicleState()
    private val requests=linkedMapOf<String,Request>()
    val display:LauncherVehicleState get()=requests.values.fold(actual) { s,r ->
        when(r.op) {
            "energy"->s.copy(energy=r.value)
            "recovery"->s.copy(recovery=r.value)
            "drive"->s.copy(drive=r.value)
            "height"->s.copy(height=listOf(9,7,5,1)[r.value],adjusting=0)
            else->s
        }
    }
    fun request(op:String,value:String):Request {
        val key=if(op=="drive" || op=="height")"drive-height" else op
        val v=if(op=="energy")requireNotNull(actual.modes[value]) else value.toInt()
        return Request(++serial,op,v,key).also {requests[key]=it}
    }
    fun accept(state:LauncherVehicleState) {
        actual=state
        requests.values.removeAll { r -> when(r.op) {
            "energy"->state.energy==r.value
            "recovery"->state.recovery==r.value
            "drive"->state.drive==r.value
            "height"->state.height==listOf(9,7,5,1)[r.value]
            else->false
        } }
    }
    fun pending(r:Request)=requests[r.key]==r
    fun finish(r:Request) {if(pending(r))requests.remove(r.key)}
}
