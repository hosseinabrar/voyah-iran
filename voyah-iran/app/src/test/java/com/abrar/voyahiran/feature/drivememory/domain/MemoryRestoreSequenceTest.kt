package com.abrar.voyahiran.feature.drivememory.domain

import org.junit.Assert.*
import org.junit.Test

class MemoryRestoreSequenceTest {
    private class Store(var value:DriveMemory):DriveMemoryStore {
        override fun read()=value
        override fun write(value:DriveMemory){this.value=value}
    }
    private val all=MemorySetting.entries.toSet()
    private fun engines(enabled:Set<MemorySetting> = all)=MemorySetting.entries.associateWith {key->
        val range=when(key){MemorySetting.RECOVERY->2..4;MemorySetting.ENERGY_MODE->1..4;MemorySetting.DRIVE->1..6;else->1..2}
        DriveMemoryEngine(Store(DriveMemory(key in enabled,2,"old")),range).apply {start("boot")}
    }
    private fun sample()=VehicleSample(mode=1,acc=2,ready=0,speed=0,recovery=4,energyMode=1,aeb=1,pedestrian=1)

    @Test fun menuOrderWaitsForConfirmationAndDriveSideEffects() {
        val powertrain=setOf(MemorySetting.DRIVE,MemorySetting.RECOVERY,MemorySetting.ENERGY_MODE)
        val engines=engines(powertrain);val sequence=MemoryRestoreSequence(engines)
        val sent=mutableListOf<MemorySetting>()
        val send={key:MemorySetting,_:Int->sent.add(key);Unit}
        var s=sample()
        sequence.process(s,0,all,send)
        assertEquals(listOf(MemorySetting.DRIVE),sent)
        sequence.process(s,100,all,send)
        assertEquals(1,sent.size)
        s=s.copy(mode=2)
        sequence.process(s,200,all,send)
        sequence.process(s,1199,all,send)
        assertEquals(1,sent.size)
        assertEquals(2,engines.getValue(MemorySetting.ENERGY_MODE).memory.mode)
        sequence.process(s,1200,all,send)
        assertEquals(MemorySetting.RECOVERY,sent.last())
        s=s.copy(recovery=2)
        sequence.process(s,1300,all,send)
        assertEquals(MemorySetting.ENERGY_MODE,sent.last())
        s=s.copy(energyMode=2)
        sequence.process(s,1400,all,send)
        s=s.copy(aeb=2)
        sequence.process(s,1500,all,send)
        assertEquals(powertrain.toList(),sent)
    }

    @Test fun assistanceMemoriesRunWithoutDriveMemoryAndRecoverFromUnavailableWriter() {
        val engines=engines(setOf(MemorySetting.AEB,MemorySetting.PEDESTRIAN))
        val sequence=MemoryRestoreSequence(engines)
        val sent=mutableListOf<MemorySetting>()
        try {sequence.process(sample(),0,all) {key,_->sent+=key;error("OEM service starting")};fail()}catch(_:IllegalStateException){}
        assertEquals(DrivePhase.FAILED,engines.getValue(MemorySetting.AEB).phase)
        sequence.process(sample(),4999,all) {key,_->sent+=key}
        assertEquals(2,sent.size)
        sequence.process(sample(),5000,all) {key,_->sent+=key}
        assertEquals(listOf(MemorySetting.AEB,MemorySetting.PEDESTRIAN,MemorySetting.AEB,MemorySetting.PEDESTRIAN),sent)
        sequence.process(sample().copy(aeb=2),5100,all) {key,_->sent+=key}
        assertEquals(MemorySetting.PEDESTRIAN,sent.last())
    }

    @Test fun retriesAreBoundedAndRequireFreshStateButNotSpeed() {
        val engines=engines(setOf(MemorySetting.AEB));val sequence=MemoryRestoreSequence(engines)
        var attempts=0
        fun failedAt(now:Long,speed:Int?=0) {try {sequence.process(sample().copy(speed=speed),now,all) {_,_->attempts++;error("not ready")}}catch(_:IllegalStateException){}}
        failedAt(0)
        sequence.process(sample().copy(aeb=null),15000,all) {_,_->fail("unknown setting")}
        sequence.process(sample(),15000,all) {_,_->fail("confirmation timeout must be observed before retry")}
        sequence.process(sample(),15000,emptySet()) {_,_->fail("stale observation")}
        failedAt(15000,10);failedAt(30000,null);failedAt(60000);failedAt(90000)
        assertEquals(3,attempts)
        assertEquals(2,engines.getValue(MemorySetting.AEB).memory.mode)
    }

    @Test fun lateConfirmationDoesNotNeedManualRetry() {
        val engines=engines(setOf(MemorySetting.AEB));val sequence=MemoryRestoreSequence(engines)
        sequence.process(sample(),0,all) {_,_->}
        sequence.process(sample(),10001,all) {_,_->fail()}
        assertEquals(DrivePhase.FAILED,engines.getValue(MemorySetting.AEB).phase)
        sequence.process(sample().copy(aeb=2),11000,all) {_,_->fail()}
        assertEquals(DrivePhase.MONITORING,engines.getValue(MemorySetting.AEB).phase)
    }

    @Test fun offRearmsEveryMemoryEvenWhenAnEarlierOneIsBlocked() {
        val engines=engines();val sequence=MemoryRestoreSequence(engines)
        sequence.process(sample().copy(acc=0),0,all) {_,_->fail()}
        assertTrue(engines.values.all {it.memory.pending&&it.phase==DrivePhase.WAITING})
        val sent=mutableListOf<MemorySetting>()
        sequence.process(sample().copy(mode=null),1000,all) {key,_->sent+=key}
        assertEquals(listOf(MemorySetting.AEB,MemorySetting.PEDESTRIAN),sent)
        assertTrue(engines.values.all {it.memory.mode==2})
    }

    @Test fun failedDriveAndAebDoNotBlockPedestrianConfirmationOrOwnRetries() {
        val engines=engines();val sequence=MemoryRestoreSequence(engines)
        val sent=mutableListOf<MemorySetting>()
        fun process(now:Long,s:VehicleSample) {
            try {sequence.process(s,now,all) {key,_->
                sent+=key
                if(key!=MemorySetting.PEDESTRIAN)error("unavailable")
            }} catch(_:IllegalStateException) {}
        }
        process(0,sample())
        assertEquals(listOf(MemorySetting.DRIVE,MemorySetting.AEB,MemorySetting.PEDESTRIAN),sent)
        process(100,sample().copy(pedestrian=2))
        assertEquals(DrivePhase.MONITORING,engines.getValue(MemorySetting.PEDESTRIAN).phase)
        process(4999,sample().copy(pedestrian=2))
        assertEquals(3,sent.size)
        process(5000,sample().copy(pedestrian=2))
        assertEquals(listOf(MemorySetting.DRIVE,MemorySetting.AEB),sent.takeLast(2))
        assertFalse(sent.contains(MemorySetting.RECOVERY))
        assertFalse(sent.contains(MemorySetting.ENERGY_MODE))
    }

    @Test fun reconnectDoesNotResetRetryBudgetInSamePowerCycle() {
        val engine=engines(setOf(MemorySetting.AEB)).getValue(MemorySetting.AEB)
        val s=sample().copy(mode=1)
        repeat(3) {index->
            engine.retryAutomatically(s,index*15000L,true)
            assertEquals(2,engine.observe(s,index*15000L,true))
            engine.requestFailed()
            engine.start("boot")
            engine.observe(s,index*15000L+10000,true)
        }
        assertFalse(engine.retryAutomatically(s,90000,true))
        assertEquals(3,engine.attempts)
        engine.observe(s.copy(acc=0),100000,true)
        assertEquals(2,engine.observe(s,100001,true))
        assertEquals(1,engine.attempts)
    }

    @Test fun slowWriterBlocksOnlyItsOwnLaneAndCannotBeDispatchedTwice() {
        val engines=engines();val sequence=MemoryRestoreSequence(engines)
        val busy=mutableSetOf<MemorySetting>()
        val sent=mutableListOf<MemorySetting>()
        val dispatch={key:MemorySetting,_:Int->sent.add(key);busy.add(key);Unit}
        sequence.process(sample(),0,all,busy,dispatch)
        assertEquals(listOf(MemorySetting.DRIVE,MemorySetting.AEB,MemorySetting.PEDESTRIAN),sent)
        sequence.process(sample(),6000,all,busy,dispatch)
        assertEquals(3,sent.size)
        busy.remove(MemorySetting.PEDESTRIAN)
        sequence.process(sample().copy(pedestrian=2),6100,all,busy,dispatch)
        assertEquals(DrivePhase.MONITORING,engines.getValue(MemorySetting.PEDESTRIAN).phase)
        assertTrue(engines.getValue(MemorySetting.DRIVE).memory.pending)
        assertFalse(sent.contains(MemorySetting.RECOVERY))
        busy.remove(MemorySetting.DRIVE)
        sequence.process(sample().copy(mode=2,pedestrian=2),6200,all,busy,dispatch)
        sequence.process(sample().copy(mode=2,pedestrian=2),7200,all,busy,dispatch)
        assertEquals(MemorySetting.RECOVERY,sent.last())
        assertTrue(engines.getValue(MemorySetting.AEB).memory.pending)
    }
}
