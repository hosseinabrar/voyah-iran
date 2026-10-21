package com.abrar.voyahiran.feature.drivememory.domain

/** Same order as page_comfort.xml. A downstream default must not replace its saved target. */
enum class MemorySetting {
    DRIVE, RECOVERY, ENERGY_MODE, AEB, PEDESTRIAN;
    fun sample(s: VehicleSample) = s.copy(mode = when(this) {
        DRIVE -> s.mode
        RECOVERY -> s.recovery
        ENERGY_MODE -> s.energyMode
        AEB -> s.aeb
        PEDESTRIAN -> s.pedestrian
    })
}

class MemoryRestoreSequence(private val engines:Map<MemorySetting,DriveMemoryEngine>,
    private val log:(String)->Unit = {}) {
    private var driveSettlesAt=0L
    fun reset(){driveSettlesAt=0}
    fun process(sample:VehicleSample, now:Long, observed:Set<MemorySetting>, apply:(MemorySetting,Int)->Unit) =
        process(sample, now, observed, emptySet(), apply)

    fun process(sample:VehicleSample, now:Long, observed:Set<MemorySetting>,
        inFlight: Set<MemorySetting>, apply:(MemorySetting,Int)->Unit) {
        if(sample.off) {
            reset()
            // All memories must see power-off, including those behind a failed/waiting step.
            engines.forEach {(key,engine)->engine.observe(key.sample(sample),now,key in observed)}
            return
        }
        // Only the powertrain settings share ordering. Failure or pending feedback in
        // any lane must not prevent another lane from observing or dispatching.
        var failure: Exception? = null
        for (lane in listOf(
            listOf(MemorySetting.DRIVE, MemorySetting.RECOVERY, MemorySetting.ENERGY_MODE),
            listOf(MemorySetting.AEB), listOf(MemorySetting.PEDESTRIAN)
        )) {
            try { processLane(lane, sample, now, observed, inFlight, apply) }
            catch (error: Exception) { if (failure == null) failure = error }
        }
        failure?.let { throw it }
    }

    private fun processLane(lane: List<MemorySetting>, sample: VehicleSample, now: Long,
        observed: Set<MemorySetting>, inFlight: Set<MemorySetting>, apply: (MemorySetting, Int) -> Unit) {
        for(key in lane) {
            val engine=engines.getValue(key)
            if(!engine.memory.enabled)continue
            if(key in inFlight)return
            if(key in listOf(MemorySetting.RECOVERY, MemorySetting.ENERGY_MODE) && engines.getValue(MemorySetting.DRIVE).memory.enabled && now<driveSettlesAt) {
                log("waiting after drive confirmation remainingMs=${driveSettlesAt-now}")
                return
            }
            val current=key.sample(sample)
            if(engine.retryAutomatically(current,now,key in observed))log("retry step=$key attempt=${engine.attempts+1}/3")
            val pending=engine.memory.pending
            val request=engine.observe(current,now,key in observed)
            if(request!=null) {
                log("dispatch step=$key target=$request attempt=${engine.attempts}/3")
                try {apply(key,request)} catch(e:Exception) {
                    engine.requestFailed()
                    throw e
                }
                // Wait for a fresh read/callback before touching the next memory.
                return
            }
            if(key==MemorySetting.DRIVE && pending && !engine.memory.pending)driveSettlesAt=now+1000
            if(pending && !engine.memory.pending)log("confirmed step=$key target=${engine.memory.mode}")
            if(engine.phase!=DrivePhase.MONITORING) {
                log("waiting step=$key phase=${engine.phase} current=${current.mode} target=${engine.memory.mode} attempts=${engine.attempts}/3 acc=${sample.acc} speed=${sample.speed}")
                return
            }
        }
    }
}
