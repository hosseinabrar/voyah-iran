package com.abrar.raha

/** Transcript gate independent of Android and the speech engine. Never maps idle speech. */
class RahaWakeGate(phrases:Set<String>,private val timeoutMs:Long=8000) {
 private val selected=phrases.map {clean(it)}.filter {it.isNotBlank()}.distinct().sortedByDescending {it.length}
 init { require(timeoutMs>0) { "Wake timeout must be positive" } }
 var active=false; private set
 private var deadline=0L
 private var waitingNext=false
 private fun clean(s:String)=s.trim().replace(Regex("\\s+")," ")
 private fun prefix(text:String)=selected.firstOrNull {text==it || text.startsWith("$it ")}
 fun partial(raw:String,now:Long):String? {
  expire(now)
  if(RahaIgnoredSpeech.matches(raw)) { reset(); return null }
  val text=clean(raw);val wake=prefix(text)
  if(!active && wake!=null){active=true;deadline=now+timeoutMs}
  return if(active) {if(wake!=null) text.removePrefix(wake).trim() else if(waitingNext) text else null} else null
 }
 fun final(raw:String,now:Long):String? {
  expire(now)
  if(RahaIgnoredSpeech.matches(raw)) { reset(); return null }
  val text=clean(raw);val wake=prefix(text)
  if(!active && wake==null)return null
  if(wake!=null && !active){active=true;deadline=now+timeoutMs}
  val command=if(wake!=null)text.removePrefix(wake).trim() else if(waitingNext)text else ""
  if(command.isNotBlank()){reset();return command}
  // A partial wake must also survive the final transcript before the next segment is admitted.
  if(wake!=null)waitingNext=true else if(!waitingNext)reset()
  return null
 }
 fun expire(now:Long):Boolean {
  if(active && now>=deadline){reset();return true}
  return false
 }
 fun reset(){active=false;waitingNext=false;deadline=0}
}
