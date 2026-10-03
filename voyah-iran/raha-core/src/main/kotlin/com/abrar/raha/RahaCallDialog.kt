package com.abrar.raha

/** Pure dialogue policy. A touch on an explicitly labelled call button is confirmation. */
class RahaCallDialog(private val clock: ()->Long) {
    data class Candidate(val id:String,val name:String)
    sealed class Step {
        data object Cancelled:Step()
        data class Choose(val query:String,val candidates:List<Candidate>):Step()
        data class Confirm(val contact:Candidate):Step()
        data class Dial(val contact:Candidate):Step()
    }
    var step:Step=Step.Cancelled; private set
    private var deadline=0L
    val waiting get()=step is Step.Choose || step is Step.Confirm
    fun begin(query:String, candidates:List<Candidate>):Step {
        val matches=RahaContacts.matching(query,candidates.map { it.name }).map { candidates[it] }
        return set(when(matches.size) { 0->Step.Cancelled; 1->Step.Confirm(matches.single()); else->Step.Choose(query,matches) })
    }
    fun speak(text:String):Step {
        if(clock()>=deadline) return cancel()
        return when(val current=step) {
            is Step.Confirm -> if(RahaContacts.confirmed(text)) set(Step.Dial(current.contact)) else cancel()
            is Step.Choose -> {
                val target=RahaContacts.target(text) ?: RahaContacts.normalize(text)
                val exact=current.candidates.filter { RahaContacts.normalize(it.name)==target }
                if(exact.size==1) set(Step.Confirm(exact.single())) else cancel()
            }
            else -> cancel()
        }
    }
    fun select(id:String):Step {
        if(clock()>=deadline) return cancel()
        val contact=when(val current=step) {
            is Step.Choose -> current.candidates.singleOrNull { it.id==id }
            is Step.Confirm -> current.contact.takeIf { it.id==id }
            else -> null
        }
        return if(contact!=null) set(Step.Dial(contact)) else cancel()
    }
    fun cancel():Step= set(Step.Cancelled)
    private fun set(next:Step):Step { step=next; deadline=clock()+10_000; return next }
}
