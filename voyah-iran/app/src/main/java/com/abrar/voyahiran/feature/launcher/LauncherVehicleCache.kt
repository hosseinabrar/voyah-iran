package com.abrar.voyahiran.feature.launcher

/** Main-thread cache: confirmed vehicle data plus independent optimistic selections. */
internal class LauncherVehicleCache {
    class Request internal constructor(internal val token:VehicleSelectionCache.Request<LauncherVehicleState>)
    private var actual=LauncherVehicleState()
    private val selections=VehicleSelectionCache(actual)
    val display:LauncherVehicleState get()=selections.display
    fun stale(){actual=actual.copy(fresh=false);selections.updateActual{it.copy(fresh=false)}}
    fun request(op:String,value:String):Request {
        val key=if(op=="drive" || op=="height")"drive-height" else op
        val v=if(op=="energy")requireNotNull(actual.modes[value]) else value.toInt()
        return Request(selections.request(key,{s->when(op){
            "energy"->s.copy(energy=v)
            "recovery"->s.copy(recovery=v)
            "drive"->s.copy(drive=v)
            "height"->s.copy(height=listOf(9,7,5,1)[v],adjusting=0)
            else->s
        }},{s->s.fresh && when(op){
            "energy"->s.energy==v
            "recovery"->s.recovery==v
            "drive"->s.drive==v
            "height"->s.height==listOf(9,7,5,1)[v]
            else->false
        }}))
    }
    fun accept(state:LauncherVehicleState) {
        actual=state
        selections.accept(state)
    }
    fun pending(r:Request)=selections.pending(r.token)
    fun finish(r:Request)=selections.finish(r.token)
}
